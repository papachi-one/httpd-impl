package one.papachi.httpd.impl.websocket;


import one.papachi.httpd.api.http.HttpConnection;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.api.websocket.WebSocketConnection;
import one.papachi.httpd.api.websocket.WebSocketFrame;
import one.papachi.httpd.api.websocket.WebSocketFrameListener;
import one.papachi.httpd.api.websocket.WebSocketHandler;
import one.papachi.httpd.api.websocket.WebSocketListener;
import one.papachi.httpd.api.websocket.WebSocketMessage;
import one.papachi.httpd.api.websocket.WebSocketMessageListener;
import one.papachi.httpd.api.websocket.WebSocketSession;
import one.papachi.httpd.api.websocket.WebSocketStream;
import one.papachi.httpd.api.websocket.WebSocketStreamListener;
import one.papachi.httpd.impl.Run;
import one.papachi.httpd.impl.net.TransferAsynchronousByteChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;

public class DefaultWebSocketConnection implements WebSocketConnection, Runnable {

    @Override
    public HttpConnection getHttpConnection() {
        return null;
    }

    @Override
    public WebSocketSession getWebSocketSession() {
        return null;
    }

    enum State {
        READ, READ_FRAME, READ_FIRST_BYTE, READ_SECOND_BYTE, READ_LENGTH, READ_MASK, PROCESS_FRAME, READ_PAYLOAD, SKIP_PAYLOAD, BREAK, ERROR
    }

    protected final AsynchronousSocketChannel channel;

    protected final ByteBuffer readBuffer;

    protected volatile State state = State.READ_FRAME, resumeState;

    protected final DefaultWebSocketSession webSocketSession;

    public DefaultWebSocketConnection(AsynchronousSocketChannel channel, ByteBuffer readBuffer, HttpRequest request, HttpResponse response, WebSocketHandler handler) {
        this.channel = channel;
        this.readBuffer = readBuffer;
        this.webSocketSession = new DefaultWebSocketSession(request, response, this);
        handler.handle(webSocketSession);
    }

    protected void run(State state) {
        this.state = state;
        run();
    }

    @Override
    public void run() {
        while (true) {
            if (state == State.READ) {
                if (readBuffer.hasRemaining()) {
                    state = resumeState;
                } else {
                    state = State.BREAK;
                    read();
                    break;
                }
            } else if (state == State.READ_FRAME) {
                state = readFrame();
            } else if (state == State.READ_FIRST_BYTE) {
                state = readFirstByte();
            } else if (state == State.READ_SECOND_BYTE) {
                state = readSecondByte();
            } else if (state == State.READ_LENGTH) {
                state = readLength();
            } else if (state == State.READ_MASK) {
                state = readMask();
            } else if (state == State.PROCESS_FRAME) {
                state = processFrame();
            } else if (state == State.READ_PAYLOAD) {
                state = readPayload();
            } else if (state == State.SKIP_PAYLOAD) {
                state = skipPayload();
            } else if (state == State.ERROR) {
                state = State.BREAK;
            }
            if (state == State.BREAK)
                break;
        }
    }

    protected State read() {
        channel.read(readBuffer.compact(), null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                if (result == -1) {
                    run(State.BREAK);
                    return;
                }
                readBuffer.flip();
                run(resumeState);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                run(State.ERROR);
            }
        });
        return State.BREAK;
    }

    protected State readFrame() {
        counter = 0;
        return State.READ_FIRST_BYTE;
    }

    protected volatile boolean isFin, isReserved1, isReserved2, isReserved3, isMask;
    protected volatile WebSocketFrame.Type type;
    protected volatile long counter, length;
    protected volatile byte[] maskingKey;

    protected State readFirstByte() {
        if (readBuffer.hasRemaining()) {
            byte b = readBuffer.get();
            this.isFin = (b & 0x80) != 0;
            this.isReserved1 = (b & 0x40) != 0;
            this.isReserved2 = (b & 0x20) != 0;
            this.isReserved3 = (b & 0x10) != 0;
            this.type = WebSocketFrame.Type.values()[b & 0xF];
            return State.READ_SECOND_BYTE;
        }
        resumeState = State.READ_FIRST_BYTE;
        return State.READ;
    }

    protected State readSecondByte() {
        if (readBuffer.hasRemaining()) {
            byte b = readBuffer.get();
            this.isMask = (b & 0x80) != 0;
            this.length = b & 0x7F;
            return State.READ_LENGTH;
        }
        resumeState = State.READ_SECOND_BYTE;
        return State.READ;
    }

    protected State readLength() {
        if (length < 126) {
            return State.READ_MASK;
        } else if ((length == 126 && readBuffer.remaining() >= 2) || readBuffer.remaining() >= 8) {
            length = length == 126 ? readBuffer.getShort() & 0xFFFF : readBuffer.getLong();
            return State.READ_MASK;
        }
        resumeState = State.READ_LENGTH;
        return State.READ;
    }

    protected State readMask() {
        if (readBuffer.remaining() >= 4) {
            maskingKey = new byte[]{readBuffer.get(), readBuffer.get(), readBuffer.get(), readBuffer.get()};
            return State.PROCESS_FRAME;
        }
        resumeState = State.READ_MASK;
        return State.READ;
    }

    protected volatile WebSocketFrame frame;
    protected volatile WebSocketMessage message;
    protected volatile WebSocketStream stream;
    protected volatile TransferAsynchronousByteChannel dataChannel;

    protected State processFrame() {

        return switch (type) {
            case TEXT_FRAME, BINARY_FRAME, CONTINUATION_FRAME -> {
                WebSocketListener listener = webSocketSession.getListener();
                if (listener instanceof WebSocketFrameListener frameListener) {
                    if (type != WebSocketFrame.Type.CONTINUATION_FRAME) {
                        dataChannel = new TransferAsynchronousByteChannel();
                        DefaultWebSocketFrame.DefaultBuilder builder = new DefaultWebSocketFrame.DefaultBuilder();
                        frame = builder.fin(isFin)
                                .mask(isMask)
                                .maskingKey(maskingKey)
                                .length(length)
                                .type(type)
                                .payload(dataChannel)
                                .build();
                        Run.async(() -> frameListener.onFrame(frame).thenRun(() -> run(State.READ_FRAME)));
                    }
                } else if (listener instanceof WebSocketMessageListener messageListener) {
                    if (type != WebSocketFrame.Type.CONTINUATION_FRAME) {
                        dataChannel = new TransferAsynchronousByteChannel();
                        DefaultWebSocketMessage.DefaultBuilder builder = new DefaultWebSocketMessage.DefaultBuilder();
                        message = builder.type(type == WebSocketFrame.Type.TEXT_FRAME ? WebSocketMessage.Type.TEXT : WebSocketMessage.Type.BINARY)
                                .payload(dataChannel)
                                .build();
                        Run.async(() -> messageListener.onMessage(message).thenRun(() -> run(State.READ_FRAME)));
                    }
                } else if (listener instanceof WebSocketStreamListener streamListener) {
                    if (dataChannel == null) {
                        dataChannel = new TransferAsynchronousByteChannel();
                        DefaultWebSocketStream.DefaultBuilder builder = new DefaultWebSocketStream.DefaultBuilder();
                        stream = builder.input(dataChannel).build();
                        Run.async(() -> streamListener.onStream(stream));
                    }
                }
                yield State.READ_PAYLOAD;
            }
            case PING_FRAME, PONG_FRAME -> {
                yield State.SKIP_PAYLOAD;
            }
            case CONNECTION_CLOSE -> {
                yield State.SKIP_PAYLOAD;
            }
            default -> State.SKIP_PAYLOAD;
        };
    }

    protected State readPayload() {
        if (counter == length) {
            try {
                dataChannel.close();
            } catch (IOException ignored) {
            }
            return State.BREAK;
        } else if (readBuffer.hasRemaining() && counter < length) {
            long lSize = (length - counter);
            int size = (int) Math.min(lSize, readBuffer.remaining());
            ByteBuffer buffer = readBuffer.duplicate();
            buffer.limit(buffer.position() + size);
            readBuffer.position(readBuffer.position() + size);
            if (isMask) {
                for (int c = 0; c < buffer.remaining(); c++) {
                    int i = buffer.get(buffer.position() + c) & 0xFF;
                    i = i ^ maskingKey[(int) (this.counter++ & 3L)];
                    byte b = (byte) i;
                    buffer.put(buffer.position() + c, b);
                }
            }
            dataChannel.write(buffer, null, new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(Integer result, Void attachment) {
                    run(State.READ_PAYLOAD);
                }
                @Override
                public void failed(Throwable exc, Void attachment) {
                    run(State.ERROR);
                }
            });
            return State.BREAK;
        }
        resumeState = State.READ_PAYLOAD;
        return State.READ;
    }

    protected State skipPayload() {
        int offset = (int) Math.min(readBuffer.remaining(), length - counter);
        readBuffer.position(readBuffer.position() + offset);
        counter += offset;
        if (counter == length) {
            return State.READ_FRAME;
        }
        resumeState = State.SKIP_PAYLOAD;
        return State.READ;
    }


    protected State connectionCloseFrame() {
        if (webSocketSession.getListener() instanceof WebSocketFrameListener handler) {
            Run.async(() -> handler.onFrame(null));
        }
        if (webSocketSession.getListener() instanceof WebSocketMessageListener handler) {
            Run.async(() -> handler.onMessage(null));
        }
        return State.BREAK;
    }

    public CompletableFuture<WebSocketSession> send(WebSocketStream src) {
        CompletableFuture<WebSocketSession> completableFuture = new CompletableFuture<>();
        ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
        long counter = 0L;
        try {
            while (true) {
                int result = src.read(buffer.clear()).get();
                buffer.flip();
                if (result == -1) {
                    ByteBuffer header = getHeader(0, true, false, counter == 0 ? 0x02 : 0x00);
                    while (header.hasRemaining()) {
                        channel.write(header).get();
                    }
                    break;
                }
                int length = buffer.remaining();
                ByteBuffer header = getHeader(length, false, false, counter == 0 ? 0x02 : 0x00);
                while (header.hasRemaining()) {
                    channel.write(header).get();
                }
                while (buffer.hasRemaining()) {
                    channel.write(buffer).get();
                    counter++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return completableFuture;
    }

    protected ByteBuffer getHeader(long length, boolean isFin, boolean isMask, int opCode) {
        byte lengthByte;
        byte[] lengthBytes;
        if (length < 126) {
            lengthByte = (byte) length;
            lengthBytes = new byte[0];
        } else if (length < 65536) {
            lengthByte = 126;
            ByteBuffer.wrap(lengthBytes = new byte[2]).putShort((short) length);
        } else {
            lengthByte = 127;
            ByteBuffer.wrap(lengthBytes = new byte[8]).putLong(length);
        }
        byte firstByte = (byte) (opCode & 0xFF);
        if (isFin)
            firstByte |= 0x80;
        byte secondByte = lengthByte;
        if (isMask)
            secondByte |= 0x80;
        return ByteBuffer.allocate(2 + lengthBytes.length).put(firstByte).put(secondByte).put(lengthBytes).flip();
    }

    public CompletableFuture<WebSocketSession> send(WebSocketMessage src) {
        CompletableFuture<WebSocketSession> completableFuture = new CompletableFuture<>();
        ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
        long counter = 0L;
        try {
            while (true) {
                int result = src.read(buffer.clear()).get();
                buffer.flip();
                if (result == -1) {
                    ByteBuffer header = getHeader(0, true, false, counter == 0 ? (src.getType() == WebSocketMessage.Type.TEXT ? 0x01 : 0x02) : 0x00);
                    while (header.hasRemaining()) {
                        channel.write(header).get();
                    }
                    break;
                }
                int length = buffer.remaining();
                ByteBuffer header = getHeader(length, false, false, counter == 0 ? (src.getType() == WebSocketMessage.Type.TEXT ? 0x01 : 0x02) : 0x00);
                while (header.hasRemaining()) {
                    channel.write(header).get();
                }
                while (buffer.hasRemaining()) {
                    channel.write(buffer).get();
                    counter++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return completableFuture;
    }

    public CompletableFuture<WebSocketSession> send(WebSocketFrame src) {
        CompletableFuture<WebSocketSession> completableFuture = new CompletableFuture<>();
        ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
        try {
            ByteBuffer header = getHeader(src.getLength(), src.isFin(), src.isMasked(), src.getType().ordinal());
            while (header.hasRemaining()) {
                channel.write(header).get();
            }
            while (true) {
                int result = src.read(buffer.clear()).get();
                if (result == -1) {
                    break;
                }
                buffer.flip();
                while (buffer.hasRemaining()) {
                    channel.write(buffer).get();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return completableFuture;
    }

}
