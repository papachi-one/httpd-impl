package one.papachi.httpd.impl.http;

import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpHeaders;
import one.papachi.httpd.api.http.HttpOptions;
import one.papachi.httpd.impl.Run;
import one.papachi.httpd.impl.StandardHttpOptions;
import one.papachi.httpd.impl.hpack.Decoder;
import one.papachi.httpd.impl.hpack.Encoder;
import one.papachi.httpd.impl.http.data.DefaultHttpBody;
import one.papachi.httpd.impl.http.data.DefaultHttpHeaders;

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
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Http2Connection {

    protected final HttpOptions options;
    protected final AsynchronousSocketChannel channel;
    protected final Http2Settings localSettings = new Http2Settings();
    protected final Http2Settings remoteSettings = new Http2Settings();
    protected final Decoder decoder = new Decoder(4096, remoteSettings.getHeaderTableSize());
    protected final Encoder encoder = new Encoder(remoteSettings.getHeaderTableSize());
    protected final Object sendWindowSizeLock = new Object();
    protected final Object receiveWindowSizeLock = new Object();
    protected int receiveWindowSize = 65535;
    protected int sendWindowSize = 65535;
    protected int consumedWindowSize;
    protected final Map<Integer, Integer> consumedWindowSizes = new HashMap<>();
    protected final Map<Integer, Integer> receiveWindowSizes = new HashMap<>();
    protected final Map<Integer, Integer> sendWindowSizes = new HashMap<>();
    protected final Http2ConnectionIO io;
    protected volatile HttpHeaders.Builder remoteHeadersBuilder;
    protected volatile HttpHeaders remoteHeaders;
    protected volatile HttpBody remoteBody;
    protected final Map<Integer, Http2RemoteBodyChannel> remoteBodyChannels = Collections.synchronizedMap(new HashMap<>());
    protected final Set<Http2LocalBodyChannel> localBodyChannels = Collections.synchronizedSet(new HashSet<>());
    protected final AtomicInteger nextStreamId = new AtomicInteger(1);
    protected volatile boolean isEndStream;

    public Http2Connection(HttpOptions options, AsynchronousSocketChannel channel, Http2ConnectionIO.Mode mode) {
        this.options = options;
        this.channel = channel;
        io = new Http2ConnectionIO(mode, channel, localSettings, this::handleFrame);
        Run.async(() -> io.run(mode == Http2ConnectionIO.Mode.SERVER ? Http2ConnectionIO.State.READ_MAGIC : Http2ConnectionIO.State.READ_FRAME_HEADER));
    }

    protected final Set<LocalBody> localBodiesReady = new LinkedHashSet<>();

    protected final Set<Http2LocalBodyChannel> localBodyWriting = new HashSet<>();

    protected record LocalBody(int streamId, Http2LocalBodyChannel localBodyChannel) {
    }

    protected void sendLocalHeaders(int streamId, List<HttpHeader> headers, HttpBody body) {
        synchronized (sendWindowSizeLock) {
            sendWindowSizes.put(streamId, remoteSettings.getInitialWindowSize());
        }
        synchronized (encoder) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            try {
                for (HttpHeader header : headers) {
                    encoder.encodeHeader(os, header.getName().toLowerCase().getBytes(StandardCharsets.US_ASCII), header.getValue().getBytes(StandardCharsets.US_ASCII), false);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            ByteBuffer buffer = ByteBuffer.wrap(os.toByteArray());
            List<ByteBuffer> buffers = new ArrayList<>();
            for (int count = 0; buffer.hasRemaining(); count++) {
                int length = Math.min(buffer.remaining(), localSettings.getMaxFrameSize());
                ByteBuffer frameHeader = ByteBuffer.allocate((9 + 1));
                frameHeader.putInt(length);
                frameHeader.put(count == 0 ? (byte) 0x01 : (byte) 0x09);
                frameHeader.put((byte) (count == 0 ? (length == buffer.remaining() ? (body != null && body.isPresent() ? 0x04 : 0x05) : (body != null && body.isPresent() ? 0x00 : 0x01)) : (length == buffer.remaining() ? 0x04 : 0x00)));
                frameHeader.putInt(streamId);
                frameHeader.flip().get();
                ByteBuffer framePayload = buffer.duplicate();
                framePayload.limit(framePayload.position() + length);
                buffer.position(buffer.position() + length);
                buffers.add(frameHeader);
                buffers.add(framePayload);
            }
            io.write(buffers);
        }
        if (body != null && body.isPresent()) {
            sendLocalBody(streamId, body);
        }
    }

    protected void sendLocalBody(int streamId, HttpBody body) {
        Http2LocalBodyChannel localBodyChannel = new Http2LocalBodyChannel(body, options.getOption(StandardHttpOptions.WRITE_BUFFER_SIZE), data -> {
            synchronized (localBodiesReady) {
                localBodiesReady.add(new LocalBody(streamId, data));
                writeLocalBody();
            }
        });
        localBodyChannels.add(localBodyChannel);
        localBodyChannel.start();
    }

    protected void writeLocalBody() {
        synchronized (localBodiesReady) {
            Iterator<LocalBody> iterator = localBodiesReady.iterator();
            while (iterator.hasNext()) {
                LocalBody localBody = iterator.next();
                if (localBodyWriting.contains(localBody.localBodyChannel)) {
                    continue;
                }
                int size;
                synchronized (sendWindowSizeLock) {
                    size = Math.min(Math.min(localSettings.getMaxFrameSize(), remoteSettings.getMaxFrameSize()), Math.min(sendWindowSize, Optional.ofNullable(sendWindowSizes.get(localBody.streamId)).orElse(0)));
                }
                if (size == 0) {
                    continue;
                }
                iterator.remove();
                Http2LocalBodyChannel.ReadResult readResult = localBody.localBodyChannel.read(size);
                int result = readResult.result();
                if (result == -1 && localBodyChannels.remove(localBody.localBodyChannel)) {
                    synchronized (sendWindowSizeLock) {
                        sendWindowSizes.remove(localBody.streamId);
                    }
                    ByteBuffer frameHeader = ByteBuffer.allocate(10);
                    frameHeader.putInt(0).put((byte) 0x00).put((byte) 0x01).putInt(localBody.streamId).flip().get();
                    Run.async(() -> io.write(frameHeader));
                } else if (result > 0) {
                    localBodyWriting.add(localBody.localBodyChannel);
                    updateSendWindowSize(-result);
                    updateSendWindowSize(localBody.streamId, -result);
                    ByteBuffer frameHeader = ByteBuffer.allocate(10);
                    frameHeader.putInt(result).put((byte) 0x00).put((byte) 0x00).putInt(localBody.streamId).flip().get();
                    Run.async(() -> io.write(() -> {
                        synchronized (localBodiesReady) {
                            localBodyWriting.remove(localBody.localBodyChannel);
                        }
                        localBody.localBodyChannel.release(result);
                    }, frameHeader, readResult.buffer()));
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
            if (!receiveWindowSizes.containsKey(streamId))
                return;
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
            if (!sendWindowSizes.containsKey(streamId))
                return;
            sendWindowSizes.put(streamId, sendWindowSizes.get(streamId) + delta);
        }
    }

    protected Http2ConnectionIO.State handleFrame(Http2Frame frame) {
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

    protected void sendMagic() {
        ByteBuffer buffer = ByteBuffer.wrap("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        io.write(buffer);
    }

    protected void sendInitialSettings() {
        int maxFrameSize = options.getOption(StandardHttpOptions.MAX_FRAME_SIZE);
        int maxConcurrentStreams = options.getOption(StandardHttpOptions.MAX_CONCURRENT_STREAMS);
        int headerTableSize = options.getOption(StandardHttpOptions.HEADER_TABLE_SIZE);
        int headerListSize = options.getOption(StandardHttpOptions.HEADER_LIST_SIZE);
        int connectionWindowSize = options.getOption(StandardHttpOptions.CONNECTION_WINDOW_SIZE);
        int connectionWindowUpdate = connectionWindowSize - 65535;
        int streamInitialWindowSize = options.getOption(StandardHttpOptions.STREAM_INITIAL_WINDOW_SIZE);

//        int maxFrameSize = StandardHttpOptions.MAX_FRAME_SIZE.defaultValue();
//        int maxConcurrentStreams = StandardHttpOptions.MAX_CONCURRENT_STREAMS.defaultValue();
//        int headerTableSize = StandardHttpOptions.HEADER_TABLE_SIZE.defaultValue();
//        int headerListSize = StandardHttpOptions.HEADER_LIST_SIZE.defaultValue();
//        int connectionWindowSize = StandardHttpOptions.CONNECTION_WINDOW_SIZE.defaultValue();
//        int connectionWindowUpdate = connectionWindowSize - 65535;
//        int streamInitialWindowSize = StandardHttpOptions.STREAM_INITIAL_WINDOW_SIZE.defaultValue();

        localSettings.setMaxFrameSize(maxFrameSize);
        localSettings.setMaxConcurrentStreams(maxConcurrentStreams);
        localSettings.setHeaderTableSize(headerTableSize);
        localSettings.setMaxHeaderListSize(headerListSize);
        localSettings.setEnablePush(0);
        localSettings.setInitialWindowSize(streamInitialWindowSize);
        receiveWindowSize = connectionWindowSize;

        {
            ByteBuffer buffer = ByteBuffer.allocate(9 + (5 * 6) + 1);
            buffer.putInt(5 * 6).put((byte) 0x04).put((byte) 0x00).putInt(0);
            buffer.putShort((short) 0x01).putInt(headerTableSize);
            buffer.putShort((short) 0x03).putInt(maxConcurrentStreams);
            buffer.putShort((short) 0x04).putInt(streamInitialWindowSize);
            buffer.putShort((short) 0x05).putInt(maxFrameSize);
            buffer.putShort((short) 0x06).putInt(headerListSize);
            buffer.flip().get();
            io.write(buffer);
        }

        if (connectionWindowUpdate > 0) {
            ByteBuffer buffer = ByteBuffer.allocate(9 + 4 + 1);
            buffer.putInt(4).put((byte) 0x08).put((byte) 0x00).putInt(0);
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
            updateReceiveWindowSize(-frame.getData().remaining());
            updateReceiveWindowSize(streamId, -frame.getData().remaining());
        }
        if (frame.isEndStream()) {
            synchronized (receiveWindowSizeLock) {
                receiveWindowSizes.remove(streamId);
            }
        }
        Http2RemoteBodyChannel bodyChannel = remoteBodyChannels.get(streamId);
        if (frame.getHeader().getLength() > 0) {
            bodyChannel.put(frame.getData());
        }
        if (frame.isEndStream()) {
            bodyChannel.closeInbound();
            remoteBodyChannels.remove(streamId);
        }
        return Http2ConnectionIO.State.READ_FRAME_HEADER;
    }

    protected Http2ConnectionIO.State processHeaders(Http2Frame frame) {
//        if (stream != null && stream.state != Http2StreamConnection.State.RESERVED_REMOTE) {
//            return Http2ConnectionIO.State.ERROR;// Http2Error.STREAM_CLOSED;
//        }
        if (frame.getHeader().getStreamId() == 0) {
            return Http2ConnectionIO.State.ERROR;// Http2Error.PROTOCOL_ERROR;
        }
//        if (streams.containsKey(streamId)) {
//            return Http2ConnectionIO.State.ERROR;// Http2Error.PROTOCOL_ERROR;
//        }
        if (frame.isPadded() && frame.getPadLength() >= frame.getHeader().getLength()) {
            return Http2ConnectionIO.State.ERROR;// Http2Error.PROTOCOL_ERROR;
        }
        remoteHeadersBuilder = new DefaultHttpHeaders.DefaultBuilder();
        isEndStream = frame.isEndStream();
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
        ByteBuffer payload = frame.getPayload();
        while (payload.hasRemaining()) {
            int identifier = payload.getShort() & 0xFFFF;
            int value = payload.getInt();
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
            byte[] settingsAckFrame = new byte[]{0x00, 0x00, 0x00, // length (24)
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
        return Http2ConnectionIO.State.ERROR;
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
        writeLocalBody();
        return Http2ConnectionIO.State.READ_FRAME_HEADER;
    }

    protected Http2ConnectionIO.State processContinuation(Http2Frame frame) {
        ByteBuffer data = frame.getData();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data.array(), data.arrayOffset() + data.position(), data.remaining());
        try {
            decoder.decode(inputStream, (nameArray, valueArray, sensitive) -> {
                String name = new String(nameArray, StandardCharsets.US_ASCII);
                String value = new String(valueArray, StandardCharsets.US_ASCII);
                remoteHeadersBuilder.header(name, value);
            });
            remoteHeaders = remoteHeadersBuilder.build();
        } catch (IOException e) {
            e.printStackTrace();
            return Http2ConnectionIO.State.ERROR;
        }
        if (frame.isEndHeaders()) {
            decoder.endHeaderBlock();
            int streamId = frame.getHeader().getStreamId();
            if (isEndStream) {
                remoteBody = new DefaultHttpBody.DefaultBuilder().empty().build();
            } else {
                Http2RemoteBodyChannel bodyChannel = new Http2RemoteBodyChannel(localSettings.getInitialWindowSize(), consumed -> {
                    synchronized (receiveWindowSizeLock) {
                        this.consumedWindowSize += consumed;
                        this.consumedWindowSizes.put(streamId, Optional.ofNullable(this.consumedWindowSizes.get(streamId)).orElse(0) + consumed);
                        if (this.consumedWindowSize > options.getOption(StandardHttpOptions.CONNECTION_WINDOW_SIZE_THRESHOLD)) {
                            updateReceiveWindowSize(this.consumedWindowSize);
                            this.consumedWindowSize = 0;
                        }
                        if (this.consumedWindowSizes.get(streamId) > options.getOption(StandardHttpOptions.STREAM_WINDOW_SIZE_THRESHOLD)) {
                            updateReceiveWindowSize(streamId, this.consumedWindowSizes.get(streamId));
                            this.consumedWindowSizes.put(streamId, 0);
                        }
                    }
                });
                remoteBody = new DefaultHttpBody.DefaultBuilder().input(bodyChannel).build();
                remoteBodyChannels.put(streamId, bodyChannel);
            }
            synchronized (receiveWindowSizeLock) {
                receiveWindowSizes.put(streamId, localSettings.getInitialWindowSize());
            }
            handleRemote(streamId);
        }
        return Http2ConnectionIO.State.READ_FRAME_HEADER;
    }

    protected abstract void handleRemote(int streamId);

    protected void closeConnection() {
        try {
            channel.close();
        } catch (IOException ignored) {
        }
    }

}
