package one.papachi.httpd.impl.websocket;


import one.papachi.httpd.api.http.HttpConnection;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.websocket.WebSocketConnection;
import one.papachi.httpd.api.websocket.WebSocketFrame;
import one.papachi.httpd.api.websocket.WebSocketFrameListener;
import one.papachi.httpd.api.websocket.WebSocketHandler;
import one.papachi.httpd.api.websocket.WebSocketMessageListener;
import one.papachi.httpd.api.websocket.WebSocketSession;
import one.papachi.httpd.api.websocket.WebSocketStreamListener;
import one.papachi.httpd.impl.Run;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultWebSocketConnection implements WebSocketConnection, Runnable {

    public enum Mode {
        CLIENT, SERVER
    }

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

    private record WriteOperation(ByteBuffer buffer, Runnable runnable) {
    }

    protected final Queue<WriteOperation> writeQueue = new LinkedList<>();

    protected final AtomicReference<Boolean> amIWriting = new AtomicReference<>(false);

    public DefaultWebSocketConnection(Mode mode, AsynchronousSocketChannel channel, ByteBuffer readBuffer, HttpRequest request, WebSocketHandler handler) {
        this.channel = channel;
        this.readBuffer = readBuffer;
        this.webSocketSession = new DefaultWebSocketSession(request, this);
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

    enum OpCode {
        CONTINUATION_FRAME, TEXT_FRAME, BINARY_FRAME, NON_CONTROL_FRAME_3, NON_CONTROL_FRAME_4, NON_CONTROL_FRAME_5, NON_CONTROL_FRAME_6, NON_CONTROL_FRAME_7, CONNECTION_CLOSE, PING_FRAME, PONG_FRAME, CONTROL_FRAME_B, CONTROL_FRAME_C, CONTROL_FRAME_D, CONTROL_FRAME_E, CONTROL_FRAME_F
    }

    protected volatile boolean isFin, isReserved1, isReserved2, isReserved3, isMask;
    protected volatile OpCode opCode;
    protected volatile long counter, length;
    protected volatile byte[] mask;

    protected State readFirstByte() {
        if (readBuffer.hasRemaining()) {
            byte b = readBuffer.get();
            this.isFin = (b & 0x80) != 0;
            this.isReserved1 = (b & 0x40) != 0;
            this.isReserved2 = (b & 0x20) != 0;
            this.isReserved3 = (b & 0x10) != 0;
            this.opCode = OpCode.values()[b & 0xF];
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
            mask = new byte[]{readBuffer.get(), readBuffer.get(), readBuffer.get(), readBuffer.get()};
            return State.PROCESS_FRAME;
        }
        resumeState = State.READ_MASK;
        return State.READ;
    }

    protected volatile DefaultWebSocketRemoteFrame frame;

    protected volatile WebSocketRemoteDataChannel dataChannel;

    protected State processFrame() {
        frame = new DefaultWebSocketRemoteFrame(isFin, isMask, WebSocketFrame.Type.values()[opCode.ordinal()], length, mask, dataChannel = new WebSocketRemoteDataChannel(mask, () -> run(State.READ_PAYLOAD)));
        if ((opCode == OpCode.TEXT_FRAME || opCode == OpCode.BINARY_FRAME) && webSocketSession.getListener() instanceof WebSocketFrameListener handler) {
            handler.onFrame(frame);
            return State.BREAK;
        }
        return switch (opCode) {
            case TEXT_FRAME, BINARY_FRAME -> textBinaryFrame();
            case CONTINUATION_FRAME -> continuationFrame();
            case PING_FRAME -> pingFrame();
            case CONNECTION_CLOSE -> connectionCloseFrame();
            default -> State.SKIP_PAYLOAD;
        };
    }

    protected State readPayload() {
        if (counter == length) {
            dataChannel.closeInbound();
            return State.READ_FRAME;
        } else if (readBuffer.hasRemaining() && counter < length) {
            long lSize = (length - counter);
            int size = (int) Math.min(lSize, readBuffer.remaining());
            counter += size;
            ByteBuffer duplicate = readBuffer.duplicate();
            duplicate.limit(duplicate.position() + size);
            readBuffer.position(readBuffer.position() + size);
            dataChannel.put(duplicate);
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

    protected volatile DefaultWebSocketRemoteMessage message;

    protected volatile DefaultWebSocketRemoteStream stream;

    protected State textBinaryFrame() {
        message = new DefaultWebSocketRemoteMessage(frame);
        if (stream == null) {
            stream = new DefaultWebSocketRemoteStream();
            if (webSocketSession.getListener() instanceof WebSocketStreamListener handler) {
                Run.async(() -> handler.onStream(stream));
            }
        }
        if (webSocketSession.getListener() instanceof WebSocketMessageListener handler) {
            Run.async(() -> handler.onMessage(message));
        }
        stream.put(message);
        return State.BREAK;
    }

    protected State continuationFrame() {
        message.put(frame);
        return State.BREAK;
    }

    protected State pingFrame() {
        return State.READ_FRAME;
    }

    protected State connectionCloseFrame() {
        Optional.ofNullable(stream).ifPresent(DefaultWebSocketRemoteStream::closeInbound);
        if (webSocketSession.getListener() instanceof WebSocketFrameListener handler) {
            Run.async(() -> handler.onFrame(null));
        }
        if (webSocketSession.getListener() instanceof WebSocketMessageListener handler) {
            Run.async(() -> handler.onMessage(null));
        }
        return State.BREAK;
    }

    public CompletableFuture<WebSocketSession> send(ByteBuffer src) {
        CompletableFuture<WebSocketSession> completableFuture = new CompletableFuture<>();
        int length = src.remaining();
        boolean isFin = true;
        boolean isMask = false;
        byte opCode = 0x02;
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
        byte firstByte = opCode;
        if (isFin)
            firstByte |= 0x80;
        byte secondByte = lengthByte;
        if (isMask)
            secondByte |= 0x80;
        ByteBuffer buffer = ByteBuffer.allocate(1 + 1 + lengthBytes.length);
        buffer.put(firstByte).put(secondByte).put(lengthBytes).flip();
        channel.write(buffer, buffer, new CompletionHandler<>() {
            @Override
            public void completed(Integer result, ByteBuffer attachment) {
                if (attachment.hasRemaining()) {
                    channel.write(attachment, attachment, this);
                } else if (attachment == buffer) {
                    channel.write(src, src, this);
                } else {
                    completableFuture.complete(webSocketSession);
                }
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                exc.printStackTrace();
            }
        });
        return completableFuture;
    }

    public CompletableFuture<WebSocketSession> send(AsynchronousByteChannel src) {
        CompletableFuture<WebSocketSession> completableFuture = new CompletableFuture<>();
        ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
        long counter = 0L;
        try {
            while (true) {
                int result = src.read(buffer.clear()).get();
                buffer.flip();
                if (result == -1) {
                    int length = 0;
                    boolean isFin = true;
                    boolean isMask = false;
                    byte opCode = 0x00;
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
                    byte firstByte = opCode;
                    if (isFin)
                        firstByte |= 0x80;
                    byte secondByte = lengthByte;
                    if (isMask)
                        secondByte |= 0x80;
                    buffer.clear().put(firstByte).put(secondByte).put(lengthBytes).flip();
                    while (buffer.hasRemaining()) {
                        channel.write(buffer).get();
                    }
                    break;
                }
                int length = buffer.remaining();
                boolean isFin = false;
                boolean isMask = false;
                byte opCode = (byte) (counter == 0 ? 0x01 : 0x00);
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
                byte firstByte = opCode;
                if (isFin)
                    firstByte |= 0x80;
                byte secondByte = lengthByte;
                if (isMask)
                    secondByte |= 0x80;
                ByteBuffer header = ByteBuffer.allocate(1 + 1 + lengthBytes.length);
                header.clear().put(firstByte).put(secondByte).put(lengthBytes).flip();
                while (header.hasRemaining()) {
                    channel.write(header).get();
                }
                while (buffer.hasRemaining()) {
                    channel.write(buffer).get();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return completableFuture;
    }

    public CompletableFuture<WebSocketSession> send(WebSocketFrame src) {
        CompletableFuture<WebSocketSession> completableFuture = new CompletableFuture<>();
        return completableFuture;
    }

}
