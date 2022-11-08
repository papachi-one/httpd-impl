package one.papachi.httpd.impl.websocket;


import one.papachi.httpd.api.http.HttpConnection;
import one.papachi.httpd.api.http.HttpServer;
import one.papachi.httpd.api.websocket.WebSocketConnection;
import one.papachi.httpd.api.websocket.WebSocketFrame;
import one.papachi.httpd.api.websocket.WebSocketFrameHandler;
import one.papachi.httpd.api.websocket.WebSocketMessageHandler;
import one.papachi.httpd.api.websocket.WebSocketSession;
import one.papachi.httpd.api.websocket.WebSocketStreamHandler;
import one.papachi.httpd.impl.Run;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Optional;

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
        READ, READ_FRAME, READ_FIRST_BYTE, READ_SECOND_BYTE, READ_LENGTH, READ_MASK, PROCESS_FRAME, READ_PAYLOAD, SKIP_PAYLOAD, BREAK, ERROR, CLOSED
    }

    private final HttpServer server;

    private final AsynchronousSocketChannel channel;

    private final ByteBuffer readBuffer;

    private volatile State state = State.READ_FRAME, resumeState;

    private final DefaultWebSocketSession webSocketSession;

    public DefaultWebSocketConnection(Mode mode, HttpServer server, AsynchronousSocketChannel channel, ByteBuffer readBuffer) {
        this.server = server;
        this.channel = channel;
        this.readBuffer = readBuffer;
        this.webSocketSession = new DefaultWebSocketSession(this);
        server.getWebSocketHandler().handle(webSocketSession);
    }

    private void run(State state) {
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

    private State read() {
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

    private State readFrame() {
        counter = 0;
        return State.READ_FIRST_BYTE;
    }

    enum OpCode {
        CONTINUATION_FRAME, TEXT_FRAME, BINARY_FRAME, NON_CONTROL_FRAME_3, NON_CONTROL_FRAME_4, NON_CONTROL_FRAME_5, NON_CONTROL_FRAME_6, NON_CONTROL_FRAME_7, CONNECTION_CLOSE, PING_FRAME, PONG_FRAME, CONTROL_FRAME_B, CONTROL_FRAME_C, CONTROL_FRAME_D, CONTROL_FRAME_E, CONTROL_FRAME_F
    }

    private volatile boolean isFin, isReserved1, isReserved2, isReserved3, isMask;
    private volatile OpCode opCode;
    private volatile long counter, length;
    private volatile byte[] mask;

    private State readFirstByte() {
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

    private State readSecondByte() {
        if (readBuffer.hasRemaining()) {
            byte b = readBuffer.get();
            this.isMask = (b & 0x80) != 0;
            this.length = b & 0x7F;
            return State.READ_LENGTH;
        }
        resumeState = State.READ_SECOND_BYTE;
        return State.READ;
    }

    private State readLength() {
        if (length < 126) {
            return State.READ_MASK;
        } else if ((length == 126 && readBuffer.remaining() >= 2) || readBuffer.remaining() >= 8) {
            length = length == 126 ? readBuffer.getShort() & 0xFFFF : readBuffer.getLong();
            return State.READ_MASK;
        }
        resumeState = State.READ_LENGTH;
        return State.READ;
    }

    private State readMask() {
        if (readBuffer.remaining() >= 4) {
            mask = new byte[]{readBuffer.get(), readBuffer.get(), readBuffer.get(), readBuffer.get()};
            return State.PROCESS_FRAME;
        }
        resumeState = State.READ_MASK;
        return State.READ;
    }

    private volatile DefaultWebSocketFrame frame;

    private volatile WebSocketRemoteDataChannel dataChannel;

    private State processFrame() {
        frame = new DefaultWebSocketFrame(isFin, isMask, WebSocketFrame.Type.values()[opCode.ordinal()], length, mask, dataChannel = new WebSocketRemoteDataChannel(mask, () -> run(State.READ_PAYLOAD)));
        if ((opCode == OpCode.TEXT_FRAME || opCode == OpCode.BINARY_FRAME) && webSocketSession.getHandler() instanceof WebSocketFrameHandler handler) {
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

    private State readPayload() {
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

    private State skipPayload() {
        int offset = (int) Math.min(readBuffer.remaining(), length - counter);
        readBuffer.position(readBuffer.position() + offset);
        counter += offset;
        if (counter == length) {
            return State.READ_FRAME;
        }
        resumeState = State.SKIP_PAYLOAD;
        return State.READ;
    }

    private volatile DefaultWebSocketMessage message;

    private volatile DefaultWebSocketStream stream;

    private State textBinaryFrame() {
        message = new DefaultWebSocketMessage(frame);
        if (stream == null) {
            stream = new DefaultWebSocketStream();
            if (webSocketSession.getHandler() instanceof WebSocketStreamHandler handler) {
                Run.async(() -> handler.onStream(stream));
            }
        }
        if (webSocketSession.getHandler() instanceof WebSocketMessageHandler handler) {
            Run.async(() -> handler.onMessage(message));
        }
        stream.put(message);
        return State.BREAK;
    }

    private State continuationFrame() {
        message.put(frame);
        return State.BREAK;
    }

    private State pingFrame() {
        return State.READ_FRAME;
    }

    private State connectionCloseFrame() {
        Optional.ofNullable(stream).ifPresent(DefaultWebSocketStream::closeInbound);
        if (webSocketSession.getHandler() instanceof WebSocketFrameHandler handler) {
            Run.async(() -> handler.onFrame(null));
        }
        if (webSocketSession.getHandler() instanceof WebSocketMessageHandler handler) {
            Run.async(() -> handler.onMessage(null));
        }
        return State.CLOSED;
    }

}
