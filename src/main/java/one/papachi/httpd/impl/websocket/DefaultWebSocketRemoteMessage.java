package one.papachi.httpd.impl.websocket;


import one.papachi.httpd.api.websocket.WebSocketFrame;
import one.papachi.httpd.api.websocket.WebSocketMessage;
import one.papachi.httpd.impl.PendingReadOperation;
import one.papachi.httpd.impl.net.GenericCompletionHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultWebSocketRemoteMessage implements WebSocketMessage {

    public static class DefaultBuilder implements Builder {

        private Type type = Type.BINARY;
        private Object input;

        @Override
        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        @Override
        public Builder input(AsynchronousByteChannel channel) {
            input = channel;
            return this;
        }

        @Override
        public Builder input(AsynchronousFileChannel channel) {
            input = channel;
            return this;
        }

        @Override
        public Builder input(ReadableByteChannel channel) {
            input = channel;
            return this;
        }

        @Override
        public Builder input(InputStream inputStream) {
            input = inputStream;
            return this;
        }

        @Override
        public WebSocketMessage build() {
            return null;
        }
    }

    private final Object lock = new Object();

    private final Type type;

    protected final AtomicReference<PendingReadOperation<?>> pendingReadOperation = new AtomicReference<>();

    protected WebSocketFrame frame;

    public DefaultWebSocketRemoteMessage(WebSocketFrame frame) {
        this.frame = frame;
        this.type = frame.getType() == WebSocketFrame.Type.TEXT_FRAME ? Type.TEXT : Type.BINARY;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
        pendingReadOperation.set(new PendingReadOperation<>(dst, attachment, handler));
        transfer();
    }

    public void put(WebSocketFrame frame) {
        synchronized (lock) {
            this.frame = frame;
            transfer();
        }
    }

    private void transfer() {
        synchronized (lock) {
            if (frame != null || pendingReadOperation.get() != null) {
                PendingReadOperation<?> readOp = pendingReadOperation.getAndSet(null);
                ByteBuffer dst = readOp.getDst();
                frame.read(dst, null, new CompletionHandler<Integer, Void>() {
                    @Override
                    public void completed(Integer result, Void attachment) {
                        if (result > 0 || (result == -1 && frame.isFin())) {
                            if ((result == -1 && frame.isFin()))
                                frame = null;
                            pendingReadOperation.set(null);
                            readOp.complete(result);
                        } else {
                            pendingReadOperation.set(readOp);
                            transfer();
                        }
                    }

                    @Override
                    public void failed(Throwable exc, Void attachment) {
                        readOp.fail(exc);
                    }
                });
            }
        }
    }

    @Override
    public Future<Integer> read(ByteBuffer dst) {
        CompletableFuture<Integer> completableFuture = new CompletableFuture<>();
        read(dst, null, new GenericCompletionHandler<>(completableFuture));
        return completableFuture;
    }

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public void close() throws IOException {

    }

}
