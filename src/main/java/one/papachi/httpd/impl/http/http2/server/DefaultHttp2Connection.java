package one.papachi.httpd.impl.http.http2.server;


import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpConnection;
import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpServer;
import one.papachi.httpd.impl.StandardHttpOptions;
import one.papachi.httpd.impl.hpack.Decoder;
import one.papachi.httpd.impl.hpack.Encoder;
import one.papachi.httpd.impl.http.DefaultHttpBody;
import one.papachi.httpd.impl.http.DefaultHttpRequest;
import one.papachi.httpd.impl.http.http2.Http2RequestBodyChannel;
import one.papachi.httpd.impl.http.http2.Http2AsynchronousByteChannel;
import one.papachi.httpd.impl.http.http2.Http2ConnectionIO;
import one.papachi.httpd.impl.http.http2.Http2Frame;
import one.papachi.httpd.impl.http.http2.Http2Setting;
import one.papachi.httpd.impl.http.http2.Http2Settings;
import one.papachi.httpd.impl.net.AsynchronousBufferedSocketChannel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DefaultHttp2Connection implements HttpConnection, Runnable {

    private final HttpServer server;
    private final AsynchronousSocketChannel channel;
    private final Http2Settings localSettings = new Http2Settings();
    private final Http2Settings remoteSettings = new Http2Settings();
    private final Decoder decoder = new Decoder(4096, remoteSettings.getHeaderTableSize());
    private final Encoder encoder = new Encoder(remoteSettings.getHeaderTableSize());
    protected final Object sendWindowSizeLock = new Object();
    protected final Object receiveWindowSizeLock = new Object();
    private int receiveWindowSize = 65535;
    private int sendWindowSize = 65535;
    private int consumedWindowSize;
    private final Map<Integer, Integer> consumedWindowSizes = new HashMap<>();
    private final Map<Integer, Integer> receiveWindowSizes = new HashMap<>();
    private final Map<Integer, Integer> sendWindowSizes = new HashMap<>();
    private final Map<Integer, Http2RequestBodyChannel> requestBodyChannels = new HashMap<>();
    private final Http2ConnectionIO io;
    protected HttpRequest.Builder requestBuilder;
    protected volatile boolean isEndStream;


    public DefaultHttp2Connection(HttpServer server, AsynchronousSocketChannel channel) {
        this.server = server;
        this.channel = channel;
        io = new Http2ConnectionIO(channel, localSettings, this::handleFrame);
    }

    @Override
    public HttpServer getHttpServer() {
        return server;
    }

    @Override
    public AsynchronousSocketChannel getSocketChannel() {
        return new AsynchronousBufferedSocketChannel(channel, io.readBuffer);
    }

    @Override
    public void run() {
        sendInitialSettings();
        io.run();
    }

    private Http2ConnectionIO.State handleFrame(Http2Frame frame) {
        return switch (frame.getHeader().getType()) {
            case DATA -> processData(frame);
            case HEADERS -> processHeaders(frame);
            case PRIORITY -> processPriority(frame);
            case RST_STREAM -> processRstStream(frame);
            case SETTINGS -> processSettings(frame);
            case PUSH_PROMISE -> processPushPromise(frame);
            case PING -> processPing(frame);
            case GOAWAY -> processGoAway(frame);
            case WINDOW_UPDATE -> processWindowUpdate(frame);
            case CONTINUATION -> processContinuation(frame);
        };
    }


    protected void updateReceiveWindowSize(int delta) {
        synchronized (receiveWindowSizeLock) {
            receiveWindowSize += delta;
            if (delta > 0) {
                ByteBuffer buffer = ByteBuffer.allocate(14);
                buffer.putInt(4).put((byte) 0x08).put((byte) 0x00).putInt(0).putInt(delta).flip().get();
                io.write(buffer);
            }
        }
    }

    protected void updateReceiveWindowSize(int streamId, int delta) {
        synchronized (receiveWindowSizeLock) {
            receiveWindowSizes.put(streamId, receiveWindowSizes.get(streamId) + delta);
            if (delta > 0) {
                ByteBuffer buffer = ByteBuffer.allocate(14);
                buffer.putInt(4).put((byte) 0x08).put((byte) 0x00).putInt(streamId).putInt(delta).flip().get();
                io.write(buffer);
            }
        }
    }

    protected void updateSendWindowSize(int delta) {
        synchronized (sendWindowSizeLock) {
            sendWindowSize += delta;
        }
    }

    protected void updateSendWindowSize(int streamId, int delta) {
        synchronized (sendWindowSizeLock) {
            sendWindowSizes.put(streamId, sendWindowSizes.get(streamId) + delta);
        }
    }

    protected void sendInitialSettings() {
        int maxFrameSize = server.getOption(StandardHttpOptions.MAX_FRAME_SIZE);
        int maxConcurrentStreams = server.getOption(StandardHttpOptions.MAX_CONCURRENT_STREAMS);
        int headerTableSize = server.getOption(StandardHttpOptions.HEADER_TABLE_SIZE);
        int headerListSize = server.getOption(StandardHttpOptions.HEADER_LIST_SIZE);
        int connectionWindowSize = server.getOption(StandardHttpOptions.CONNECTION_WINDOW_SIZE);
        int connectionWindowUpdate = connectionWindowSize - 65535;
        int streamInitialWindowSize = server.getOption(StandardHttpOptions.STREAM_INITIAL_WINDOW_SIZE);

        localSettings.setMaxFrameSize(maxFrameSize);
        localSettings.setMaxConcurrentStreams(maxConcurrentStreams);
        localSettings.setHeaderTableSize(headerTableSize);
        localSettings.setMaxHeaderListSize(headerListSize);
        localSettings.setEnablePush(0);
        localSettings.setInitialWindowSize(streamInitialWindowSize);
        receiveWindowSize = connectionWindowSize;

        ByteBuffer buffer = ByteBuffer.allocate(9 + (5 * 6) + 1);
        buffer.putInt(5 * 6).put((byte) 0x04).put((byte) 0x00).putInt(0);
        buffer.putShort((short) 0x01).putInt(headerTableSize);
        buffer.putShort((short) 0x03).putInt(maxConcurrentStreams);
        buffer.putShort((short) 0x04).putInt(streamInitialWindowSize);
        buffer.putShort((short) 0x05).putInt(maxFrameSize);
        buffer.putShort((short) 0x06).putInt(headerListSize);
        buffer.flip().get();
        io.write(buffer);

        if (connectionWindowUpdate > 0) {
            buffer = ByteBuffer.allocate(9 + 4 + 1);
            buffer.putInt(8).put((byte) 0x08).put((byte) 0x00).putInt(0);
            buffer.putInt(connectionWindowUpdate);
            buffer.flip().get();
            io.write(buffer);
        }
    }
    protected Http2ConnectionIO.State processData(Http2Frame frame) {
//        if (stream == null || !(stream.state == Http2StreamConnection.State.OPEN || stream.state == Http2StreamConnection.State.HALF_CLOSED_LOCAL)) {
//            return Http2ConnectionIO.State.ERROR;// Http2Error.STREAM_CLOSED;
//        }
        int streamId = frame.getHeader().getStreamId();
        if (streamId == 0) {
            return Http2ConnectionIO.State.ERROR;// Http2Error.PROTOCOL_ERROR;
        }
        synchronized (receiveWindowSizeLock) {
            if (frame.getHeader().getLength() > receiveWindowSize || frame.getHeader().getLength() > Optional.ofNullable(receiveWindowSizes.get(streamId)).orElse(0)) {
                return Http2ConnectionIO.State.ERROR;// Http2Error.FLOW_CONTROL_ERROR;
            }
        }
        if (frame.isPadded() && frame.getPadLength() >= frame.getHeader().getLength()) {
            return Http2ConnectionIO.State.ERROR;// Http2Error.PROTOCOL_ERROR;
        }
        synchronized (receiveWindowSizeLock) {
            updateReceiveWindowSize(-frame.getPayload().length);
            updateReceiveWindowSize(streamId, -frame.getPayload().length);
        }
        Http2RequestBodyChannel bodyChannel = requestBodyChannels.get(streamId);
        bodyChannel.put(frame.getPayload());
        if (frame.isEndStream()) {
            bodyChannel.closeInbound();
            requestBodyChannels.remove(streamId);
        }
        return Http2ConnectionIO.State.READ_FRAME_HEADER;
    }

    protected Http2ConnectionIO.State processHeaders(Http2Frame frame) {
//        if (stream != null && stream.state != Http2StreamConnection.State.RESERVED_REMOTE) {
//            // Http2Error.STREAM_CLOSED;
//            return Http2ConnectionIO.State.ERROR;
//        }
        if (frame.getHeader().getStreamId() == 0) {
            return Http2ConnectionIO.State.ERROR;// Http2Error.PROTOCOL_ERROR;
        }
//        if (streams.containsKey(streamId)) {
//            // Http2Error.PROTOCOL_ERROR;
//            return Http2ConnectionIO.State.ERROR;
//        }
        if (frame.isPadded() && frame.getPadLength() >= frame.getHeader().getLength()) {
            return Http2ConnectionIO.State.ERROR;// Http2Error.PROTOCOL_ERROR;
        }
        isEndStream = frame.isEndStream();
        requestBuilder = new DefaultHttpRequest.DefaultBuilder();
        processContinuation(frame);
        return Http2ConnectionIO.State.READ_FRAME_HEADER;
    }

    protected void createStream(int streamId, boolean isEndStream) {
        synchronized (sendWindowSizeLock) {
            receiveWindowSizes.put(streamId, localSettings.getInitialWindowSize());
            sendWindowSizes.put(streamId, remoteSettings.getInitialWindowSize());
        }

        {
            HttpBody body;
            Http2RequestBodyChannel bodyChannel = null;
            if (isEndStream) {
                body = new DefaultHttpBody.DefaultBuilder().setEmpty().build();
            } else {
                bodyChannel = new Http2RequestBodyChannel(localSettings.getInitialWindowSize(), consumed -> {
                    synchronized (receiveWindowSizeLock) {
                        this.consumedWindowSize += consumed;
                        this.consumedWindowSizes.put(streamId, Optional.ofNullable(this.consumedWindowSizes.get(streamId)).orElse(0) + consumed);
                        if (this.consumedWindowSize > server.getOption(StandardHttpOptions.CONNECTION_WINDOW_SIZE_THRESHOLD)) {
                            updateReceiveWindowSize(this.consumedWindowSize);
                            this.consumedWindowSize = 0;
                        }
                        if (this.consumedWindowSizes.get(streamId) > server.getOption(StandardHttpOptions.STREAM_WINDOW_SIZE_THRESHOLD)) {
                            updateReceiveWindowSize(streamId, this.consumedWindowSizes.get(streamId));
                            this.consumedWindowSizes.put(streamId, 0);
                        }
                    }
                });
                body = new DefaultHttpBody.DefaultBuilder().setInput(bodyChannel).build();
            }
            requestBodyChannels.put(streamId, bodyChannel);
            requestBuilder.setBody(body);
        }
        HttpRequest request = requestBuilder.build();
        requestBuilder = null;

        server.getHttpHandler().handle(request).whenCompleteAsync((response, e) -> {
            if (e != null) {
                e.printStackTrace();
                return;
            }
            HttpBody body = response.getHttpBody();
            synchronized (encoder) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                try {
                    encoder.encodeHeader(os, ":status".getBytes(), Integer.toString(response.getStatusCode()).getBytes(), false);
                    for (HttpHeader header : response.getHeaders()) {
                        if (header.getName().equalsIgnoreCase("transfer-encoding") || header.getName().equalsIgnoreCase("connection"))
                            continue;
                        encoder.encodeHeader(os, header.getName().toLowerCase().getBytes(), header.getValue().getBytes(), false);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                ByteBuffer headers = ByteBuffer.wrap(os.toByteArray());
                List<ByteBuffer> buffers = new ArrayList<>();
                for (int count = 0; headers.hasRemaining(); count++) {
                    int length = Math.min(headers.remaining(), localSettings.getMaxFrameSize());
                    ByteBuffer buffer = ByteBuffer.allocate(9 + length + 1);
                    buffer.putInt(length)
                            .put(count == 0 ? (byte) 0x01 : (byte) 0x09) // HEADERS, CONTINUATION
                            .put((byte) (count == 0 ? (length == headers.remaining() ? (body != null && body.isPresent() ? 0x04 : 0x05) : (body != null && body.isPresent() ? 0x00 : 0x01)) : (length == headers.remaining() ? 0x04 : 0x00)))
                            .putInt(streamId);
                    while (buffer.hasRemaining()) {
                        buffer.put(headers.get());
                    }
                    buffer.flip().get();
                    buffers.add(buffer);
                }
                io.write(buffers);
            }
            if (body != null && body.isPresent()) {
                sendBody(streamId, body);
            }
        });
    }

    private final Set<Duo> ready = new LinkedHashSet<>();

    record Duo(int streamId, Http2AsynchronousByteChannel data) {}

    protected void sendBody(int streamId, HttpBody body) {
        int bufferSize = server.getOption(StandardHttpOptions.WRITE_BUFFER_SIZE);
        new Http2AsynchronousByteChannel(body, bufferSize, data -> {
            synchronized (ready) {
                ready.add(new Duo(streamId, data));
                writeBody();
            }
        });
    }

    private final Set<Http2AsynchronousByteChannel> inProgress = new HashSet<>();

    protected void writeBody() {
        synchronized (ready) {
            Iterator<Duo> iterator = ready.iterator();
            while (iterator.hasNext()) {
                Duo duo = iterator.next();
                if (inProgress.contains(duo.data)) {
                    continue;
                }
                int size;
                synchronized (sendWindowSizeLock) {
                    size = Math.min(Math.min(localSettings.getMaxFrameSize(), remoteSettings.getMaxFrameSize()), Math.min(sendWindowSize, sendWindowSizes.get(duo.streamId)));
                }
                if (size == 0) {
                    continue;
                }
                iterator.remove();
                Http2AsynchronousByteChannel.ReadResult readResult = duo.data.read(size);
                int result = readResult.result();
                if (result == -1) {
                    ByteBuffer frameHeader = ByteBuffer.allocate(10);
                    frameHeader.putInt(0).put((byte) 0x00).put((byte) 0x01).putInt(duo.streamId).flip().get();
                    io.write(frameHeader);
                } else if (result > 0) {
                    inProgress.add(duo.data);
                    updateSendWindowSize(-result);
                    updateSendWindowSize(duo.streamId, -result);
                    ByteBuffer frameHeader = ByteBuffer.allocate(10);
                    frameHeader.putInt(result).put((byte) 0x00).put((byte) 0x00).putInt(duo.streamId).flip().get();
                    io.write(() -> {
                        inProgress.remove(duo.data);
                        duo.data.release(result);
                    }, frameHeader, readResult.buffer());
                }
            }
        }
    }

    protected Http2ConnectionIO.State processPriority(Http2Frame frame) {
        if (frame.getHeader().getStreamId() == 0) {
            return Http2ConnectionIO.State.ERROR;// Http2Error.PROTOCOL_ERROR;
        }
        if (frame.getHeader().getLength() > 5) {
            return Http2ConnectionIO.State.ERROR;// Http2Error.FRAME_SIZE_ERROR;
        }
        return Http2ConnectionIO.State.READ_FRAME_HEADER;
    }

    protected Http2ConnectionIO.State processRstStream(Http2Frame frame) {
        if (frame.getHeader().getStreamId() == 0) {
            return Http2ConnectionIO.State.ERROR;// Http2Error.PROTOCOL_ERROR;
        }
        if (frame.getHeader().getLength() > 4) {
            return Http2ConnectionIO.State.ERROR;// Http2Error.FRAME_SIZE_ERROR;
        }

        return Http2ConnectionIO.State.READ_FRAME_HEADER;
        /* TODO
        RST_STREAM frames MUST NOT be sent for a stream in the "idle" Http2ConnectionIO.state. If a RST_STREAM frame identifying an idle
        stream is received, the recipient MUST treat this as a connection error (Section 5.4.1) of type PROTOCOL_ERROR.
         */
    }

    protected Http2ConnectionIO.State processSettings(Http2Frame frame) {
        if (frame.getHeader().getStreamId() != 0) {
            return Http2ConnectionIO.State.ERROR;// Http2Error.PROTOCOL_ERROR;
        }
        if (frame.getHeader().getLength() % 6 != 0) {
            return Http2ConnectionIO.State.ERROR;// Http2Error.FRAME_SIZE_ERROR;
        }
        if (frame.isAck() && frame.getHeader().getLength() > 0) {
            return Http2ConnectionIO.State.ERROR;// Http2Error.FRAME_SIZE_ERROR;
        }
        byte[] payload = frame.getPayload();
        int offset = 0;
        for (int i = 0; i < payload.length / 6; i++) {
            int identifier = 0;
            int value = 0;
            for (int j = 0; j < 2; j++) {
                identifier = (identifier << 8) + (payload[offset++] & 0xFF);
            }
            for (int j = 0; j < 4; j++) {
                value = (value << 8) + (payload[offset++] & 0xFF);
            }
            Http2Setting setting = Http2Setting.values()[identifier];
            switch (setting) {
                case HEADER_TABLE_SIZE -> {
                    remoteSettings.setHeaderTableSize(value);
                    decoder.setMaxHeaderTableSize(value);
                }
                case ENABLE_PUSH -> remoteSettings.setEnablePush(value);
                case MAX_CONCURRENT_STREAMS -> remoteSettings.setMaxConcurrentStreams(value);
                case INITIAL_WINDOW_SIZE -> remoteSettings.setInitialWindowSize(value);
                case MAX_FRAME_SIZE -> remoteSettings.setMaxFrameSize(value);
                case MAX_HEADER_LIST_SIZE -> remoteSettings.setMaxHeaderListSize(value);
            }
        }
        if (!frame.isAck()) {
            byte[] settingsAckFrame = new byte[]{
                    0x00, 0x00, 0x00, // length (24)
                    0x04, // type (8)
                    0x01, // flags (8)
                    0x00, 0x00, 0x00, 0x00 // reserved (1), streamId (31)
            };
            ByteBuffer writeBuffer = ByteBuffer.wrap(settingsAckFrame);
            io.write(writeBuffer);
        }
        return Http2ConnectionIO.State.READ_FRAME_HEADER;
    }

    protected Http2ConnectionIO.State processPushPromise(Http2Frame frame) {
        if (frame.getHeader().getStreamId() == 0) {
            return Http2ConnectionIO.State.ERROR;// Http2Error.PROTOCOL_ERROR;
        }
        return Http2ConnectionIO.State.ERROR;// Http2Error.PROTOCOL_ERROR;
    }

    protected Http2ConnectionIO.State processPing(Http2Frame frame) {
        if (frame.getHeader().getStreamId() != 0) {
            return Http2ConnectionIO.State.ERROR;// Http2Error.PROTOCOL_ERROR;
        }
        if (frame.getHeader().getLength() != 8) {
            return Http2ConnectionIO.State.ERROR;// Http2Error.FRAME_SIZE_ERROR;
        }
        if (!frame.isAck()) {
            ByteBuffer buffer = ByteBuffer.allocate(18);
            buffer.putInt(8).put((byte) 0x06).put((byte) 0x01).putInt(0).put(frame.getPayload()).flip().get();
            io.write(buffer);
        }
        return Http2ConnectionIO.State.READ_FRAME_HEADER;
    }

    protected Http2ConnectionIO.State processGoAway(Http2Frame frame) {
        if (frame.getHeader().getStreamId() != 0) {
            return Http2ConnectionIO.State.ERROR;// Http2Error.PROTOCOL_ERROR;
        }
        return Http2ConnectionIO.State.READ_FRAME_HEADER;
    }

    protected Http2ConnectionIO.State processWindowUpdate(Http2Frame frame) {
        if (frame.getWindowSizeIncrement() == 0) {
            return Http2ConnectionIO.State.ERROR;// Http2Error.PROTOCOL_ERROR;
        }
        if (frame.getHeader().getLength() != 4) {
            return Http2ConnectionIO.State.ERROR;// Http2Error.FRAME_SIZE_ERROR;
        }
        int streamId = frame.getHeader().getStreamId();
        int windowSizeIncrement = frame.getWindowSizeIncrement();
        if (streamId == 0) {
            updateSendWindowSize(windowSizeIncrement);
        } else {
            updateSendWindowSize(streamId, windowSizeIncrement);
        }
        writeBody();
        return Http2ConnectionIO.State.READ_FRAME_HEADER;
    }

    protected Http2ConnectionIO.State processContinuation(Http2Frame frame) {
        ByteBuffer data = frame.getData();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data.array(), data.arrayOffset() + data.position(), data.remaining());
        try {
            requestBuilder.setVersion("HTTP/2");
            decoder.decode(inputStream, (nameArray, valueArray, sensitive) -> {
                String name = new String(nameArray, StandardCharsets.US_ASCII);
                String value = new String(valueArray, StandardCharsets.US_ASCII);
                if (name.equals(":method"))
                    requestBuilder.setMethod(value);
                else if (name.equals(":path"))
                    requestBuilder.setPath(value);
                else
                    requestBuilder.addHeader(name, value);
            });
        } catch (IOException e) {
            e.printStackTrace();
            return Http2ConnectionIO.State.ERROR;
        }
        if (frame.isEndHeaders()) {
            decoder.endHeaderBlock();
            createStream(frame.getHeader().getStreamId(), isEndStream);
        }
        return Http2ConnectionIO.State.READ_FRAME_HEADER;
    }

}
