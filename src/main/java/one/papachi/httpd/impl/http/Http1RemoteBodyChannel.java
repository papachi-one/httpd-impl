package one.papachi.httpd.impl.http;


import one.papachi.httpd.impl.PendingReadOperation;
import one.papachi.httpd.impl.Run;
import one.papachi.httpd.impl.net.GenericCompletionHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class Http1RemoteBodyChannel implements AsynchronousByteChannel {

    private final Object lock = new Object();

    protected final AtomicReference<PendingReadOperation<?>> pendingReadOperation = new AtomicReference<>();

    protected final Runnable listener;

    protected ByteBuffer readBuffer = ByteBuffer.allocate(0);
    protected volatile boolean closed;

    public Http1RemoteBodyChannel(Runnable listener) {
        this.listener = listener;
    }

    @Override
    public Future<Integer> read(ByteBuffer dst) {
        CompletableFuture<Integer> completableFuture = new CompletableFuture<>();
        read(dst, null, new GenericCompletionHandler<>(completableFuture));
        return completableFuture;
    }

    @Override
    public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
        pendingReadOperation.set(new PendingReadOperation<>(dst, attachment, handler));
        transfer();
    }

    public void put(ByteBuffer buffer) {
        synchronized (lock) {
            this.readBuffer = buffer;
        }
        transfer();
    }

    public void closeInbound() {
        closed = true;
        transfer();
    }

    private void transfer() {
        synchronized (lock) {
            if (closed && !readBuffer.hasRemaining()) {
                Run.async(() -> Optional.ofNullable(pendingReadOperation.getAndSet(null)).ifPresent(op -> op.complete(-1)));
                return;
            }
            if (pendingReadOperation.get() != null) {
                if (!readBuffer.hasRemaining()) {
                    Run.async(listener);
                    return;
                }
                PendingReadOperation<?> readOp = pendingReadOperation.getAndSet(null);
                ByteBuffer dst = readOp.getDst();
                int counter = 0;
                while (dst.hasRemaining() && readBuffer.hasRemaining()) {
                    dst.put(readBuffer.get());
                    counter++;
                }
                int result = counter;
                Run.async(() -> readOp.complete(result));
            }
        }
    }

    @Override
    public Future<Integer> write(ByteBuffer src) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            ByteBuffer buffer = ByteBuffer.allocate(8 * 1024);
            try {
                while (read(buffer.clear()).get() != -1);
            } catch (InterruptedException | ExecutionException e) {
            } finally {
                closed = true;
            }
        }
    }

}
