package one.papachi.httpd.impl.http.client;

import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpClient;
import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.impl.StandardHttpOptions;
import one.papachi.httpd.impl.hpack.Decoder;
import one.papachi.httpd.impl.hpack.Encoder;
import one.papachi.httpd.impl.http.DefaultHttpBody;
import one.papachi.httpd.impl.http.DefaultHttpResponse;
import one.papachi.httpd.impl.http.Http2LocalBodyChannel;
import one.papachi.httpd.impl.http.Http2ConnectionIO;
import one.papachi.httpd.impl.http.Http2Frame;
import one.papachi.httpd.impl.http.Http2RemoteBodyChannel;
import one.papachi.httpd.impl.http.Http2Setting;
import one.papachi.httpd.impl.http.Http2Settings;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class Http2ClientConnection {

    private final HttpClient client;
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
    private final Http2ConnectionIO io;
    private final Map<Integer, Http2RemoteBodyChannel> responseBodyChannels = Collections.synchronizedMap(new HashMap<>());
    protected volatile boolean isEndStream;
    protected volatile HttpRequest request;
    protected volatile HttpResponse.Builder responseBuilder;
    protected final Map<Integer, CompletableFuture<HttpResponse>> completableFutures = Collections.synchronizedMap(new HashMap<>());
    protected final AtomicInteger nextStreamId = new AtomicInteger(1);

    public Http2ClientConnection(HttpClient client, AsynchronousSocketChannel channel) {
        this.client = client;
        this.channel = channel;
        io = new Http2ConnectionIO(Http2ConnectionIO.Mode.CLIENT, channel, localSettings, this::handleFrame);
        sendMagicAndInitialSettings();
    }

    public CompletableFuture<HttpResponse> send(HttpRequest request) {
        int streamId = nextStreamId.getAndAdd(2);
        synchronized (receiveWindowSizeLock) {
            receiveWindowSizes.put(streamId, localSettings.getInitialWindowSize());
        }
        synchronized (sendWindowSizeLock) {
            sendWindowSizes.put(streamId, remoteSettings.getInitialWindowSize());
        }
        this.request = request;
        CompletableFuture<HttpResponse> completableFuture = new CompletableFuture<>();
        HttpBody body = request.getHttpBody();
        synchronized (encoder) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            try {
                encoder.encodeHeader(os, ":method".getBytes(), request.getMethod().getBytes(), false);
                encoder.encodeHeader(os, ":path".getBytes(), request.getPath().getBytes(), false);
                encoder.encodeHeader(os, ":scheme".getBytes(), "https".getBytes(), false);
                if (request.getHeaderValue("host") != null)
                    encoder.encodeHeader(os, ":authority".getBytes(), request.getHeaderValue("host").getBytes(), false);
                for (HttpHeader header : request.getHeaders()) {
                    if (header.getName().equalsIgnoreCase("transfer-encoding") || header.getName().equalsIgnoreCase("connection") || header.getName().equalsIgnoreCase("host"))
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
        completableFutures.put(streamId, completableFuture);
        return completableFuture;
    }

    private final Set<Duo> ready = new LinkedHashSet<>();

    record Duo(int streamId, Http2LocalBodyChannel data) {}

    protected void sendBody(int streamId, HttpBody body) {
        int bufferSize = client.getOption(StandardHttpOptions.WRITE_BUFFER_SIZE);
        new Http2LocalBodyChannel(body, bufferSize, data -> {
            synchronized (ready) {
                ready.add(new Duo(streamId, data));
                writeBody();
            }
        });
    }

    private final Set<Http2LocalBodyChannel> inProgress = new HashSet<>();

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
                Http2LocalBodyChannel.ReadResult readResult = duo.data.read(size);
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
                        synchronized (ready) {
                            inProgress.remove(duo.data);
                        }
                        duo.data.release(result);
                    }, frameHeader, readResult.buffer());
                } else {
                    System.err.println("ERROR duo = " + duo);
                }
            }
        }
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

    private void sendMagicAndInitialSettings() {
        ByteBuffer buffer = ByteBuffer.wrap("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        io.write(buffer);

        int maxFrameSize = client.getOption(StandardHttpOptions.MAX_FRAME_SIZE);
        int maxConcurrentStreams = client.getOption(StandardHttpOptions.MAX_CONCURRENT_STREAMS);
        int headerTableSize = client.getOption(StandardHttpOptions.HEADER_TABLE_SIZE);
        int headerListSize = client.getOption(StandardHttpOptions.HEADER_LIST_SIZE);
        int connectionWindowSize = client.getOption(StandardHttpOptions.CONNECTION_WINDOW_SIZE);
        int connectionWindowUpdate = connectionWindowSize - 65535;
        int streamInitialWindowSize = client.getOption(StandardHttpOptions.STREAM_INITIAL_WINDOW_SIZE);

        localSettings.setMaxFrameSize(maxFrameSize);
        localSettings.setMaxConcurrentStreams(maxConcurrentStreams);
        localSettings.setHeaderTableSize(headerTableSize);
        localSettings.setMaxHeaderListSize(headerListSize);
        localSettings.setEnablePush(0);
        localSettings.setInitialWindowSize(streamInitialWindowSize);
        receiveWindowSize = connectionWindowSize;

        buffer = ByteBuffer.allocate(9 + (5 * 6) + 1);
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
        Http2RemoteBodyChannel bodyChannel = responseBodyChannels.get(streamId);
        if (frame.getHeader().getLength() > 0)
            bodyChannel.put(frame.getPayload());
        if (frame.isEndStream()) {
            bodyChannel.closeInbound();
            responseBodyChannels.remove(streamId);
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
        responseBuilder = new DefaultHttpResponse.DefaultBuilder();
        processContinuation(frame);
        return Http2ConnectionIO.State.READ_FRAME_HEADER;
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
            responseBuilder.setVersion("HTTP/2");
            decoder.decode(inputStream, (nameArray, valueArray, sensitive) -> {
                String name = new String(nameArray, StandardCharsets.US_ASCII);
                String value = new String(valueArray, StandardCharsets.US_ASCII);
                if (name.equals(":status")) {
                    responseBuilder.setStatusCode(Integer.parseInt(value));
                } else {
                    responseBuilder.addHeader(name, value);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            return Http2ConnectionIO.State.ERROR;
        }
        if (frame.isEndHeaders()) {
            decoder.endHeaderBlock();
            int streamId = frame.getHeader().getStreamId();
            HttpBody body;
            if (isEndStream) {
                body = new DefaultHttpBody.DefaultBuilder().setEmpty().build();
            } else {
                Http2RemoteBodyChannel bodyChannel = new Http2RemoteBodyChannel(localSettings.getInitialWindowSize(), consumed -> {
                    synchronized (receiveWindowSizeLock) {
                        this.consumedWindowSize += consumed;
                        this.consumedWindowSizes.put(streamId, Optional.ofNullable(this.consumedWindowSizes.get(streamId)).orElse(0) + consumed);
                        if (this.consumedWindowSize > client.getOption(StandardHttpOptions.CONNECTION_WINDOW_SIZE_THRESHOLD)) {
                            updateReceiveWindowSize(this.consumedWindowSize);
                            this.consumedWindowSize = 0;
                        }
                        if (this.consumedWindowSizes.get(streamId) > client.getOption(StandardHttpOptions.STREAM_WINDOW_SIZE_THRESHOLD)) {
                            updateReceiveWindowSize(streamId, this.consumedWindowSizes.get(streamId));
                            this.consumedWindowSizes.put(streamId, 0);
                        }
                    }
                });
                body = new DefaultHttpBody.DefaultBuilder().setInput(bodyChannel).build();
                responseBodyChannels.put(streamId, bodyChannel);
            }
            responseBuilder.setBody(body);
            HttpResponse response = responseBuilder.build();
            responseBuilder = null;
            completableFutures.remove(streamId).completeAsync(() -> response);
        }
        return Http2ConnectionIO.State.READ_FRAME_HEADER;
    }

}
