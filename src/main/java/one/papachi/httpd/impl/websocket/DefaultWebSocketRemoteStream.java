package one.papachi.httpd.impl.websocket;


import one.papachi.httpd.api.websocket.WebSocketMessage;
import one.papachi.httpd.api.websocket.WebSocketStream;
import one.papachi.httpd.impl.PendingReadOperation;
import one.papachi.httpd.impl.Run;
import one.papachi.httpd.impl.net.GenericCompletionHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultWebSocketRemoteStream implements WebSocketStream {

    private final Object lock = new Object();

    protected final AtomicReference<PendingReadOperation<?>> pendingReadOperation = new AtomicReference<>();

    protected WebSocketMessage message;

    protected volatile boolean closed;

    @Override
    public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
        pendingReadOperation.set(new PendingReadOperation<>(dst, attachment, handler));
        transfer();
    }

    public void put(WebSocketMessage message) {
        synchronized (lock) {
            this.message = message;
            transfer();
        }
    }

    public void closeInbound() {
        closed = true;
        transfer();
    }

    private void transfer() {
        synchronized (lock) {
            if (closed) {
                Run.async(() -> Optional.ofNullable(pendingReadOperation.getAndSet(null)).ifPresent(op -> op.complete(-1)));
                return;
            }
            if (message != null && pendingReadOperation.get() != null) {
                PendingReadOperation<?> readOp = pendingReadOperation.getAndSet(null);
                ByteBuffer dst = readOp.getDst();
                message.read(dst, null, new CompletionHandler<Integer, Void>() {
                    @Override
                    public void completed(Integer result, Void attachment) {
                        if (result == -1) {
                            message = null;
                            pendingReadOperation.set(readOp);
                        } else {
                            readOp.complete(result);
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
