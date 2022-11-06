package one.papachi.httpd.impl.net;


import one.papachi.httpd.impl.PendingReadOperation;
import one.papachi.httpd.impl.PendingWriteOperation;
import one.papachi.httpd.impl.Run;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class TransferAsynchronousByteChannel implements AsynchronousByteChannel {

    private final Object lock = new Object();

    private final AtomicReference<PendingReadOperation<?>> pendingReadOperation = new AtomicReference<>();

    private final AtomicReference<PendingWriteOperation<?>> pendingWriteOperation = new AtomicReference<>();

    private volatile boolean isClosed;

    @Override
    public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
        pendingReadOperation.set(new PendingReadOperation<>(dst, attachment, handler));
        transfer();
    }

    @Override
    public <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
        pendingWriteOperation.set(new PendingWriteOperation<>(src, attachment, handler));
        transfer();
    }

    private void transfer() {
        synchronized (lock) {
            if (isClosed) {
                Run.async(() -> Optional.ofNullable(pendingReadOperation.getAndSet(null)).ifPresent(op -> op.complete(-1)));
                Run.async(() -> Optional.ofNullable(pendingWriteOperation.getAndSet(null)).ifPresent(op -> op.fail(new ClosedChannelException())));
                return;
            }
            if (pendingReadOperation.get() != null && pendingWriteOperation.get() != null) {
                PendingReadOperation<?> readOp = pendingReadOperation.getAndSet(null);
                PendingWriteOperation<?> writeOp = pendingWriteOperation.getAndSet(null);
                ByteBuffer dst = readOp.getDst();
                ByteBuffer src = writeOp.getSrc();
                int counter = 0;
                while (dst.hasRemaining() && src.hasRemaining()) {
                    dst.put(src.get());
                    counter++;
                }
                int result = counter;
                Run.async(() -> readOp.complete(result));
                Run.async(() -> writeOp.complete(result));
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
    public Future<Integer> write(ByteBuffer src) {
        CompletableFuture<Integer> completableFuture = new CompletableFuture<>();
        write(src, null, new GenericCompletionHandler<>(completableFuture));
        return completableFuture;
    }

    @Override
    public boolean isOpen() {
        return !isClosed;
    }

    @Override
    public void close() throws IOException {
        isClosed = true;
        transfer();
    }

}
