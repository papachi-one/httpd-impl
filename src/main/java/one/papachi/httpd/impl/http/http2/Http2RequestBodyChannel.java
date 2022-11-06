 package one.papachi.httpd.impl.http.http2;


 import one.papachi.httpd.impl.PendingReadOperation;
 import one.papachi.httpd.impl.Run;
 import one.papachi.httpd.impl.net.GenericCompletionHandler;

 import java.io.IOException;
 import java.nio.ByteBuffer;
 import java.nio.channels.AsynchronousByteChannel;
 import java.nio.channels.CompletionHandler;
 import java.util.Optional;
 import java.util.concurrent.CompletableFuture;
 import java.util.concurrent.Future;
 import java.util.concurrent.atomic.AtomicReference;
 import java.util.function.Consumer;

public class Http2RequestBodyChannel implements AsynchronousByteChannel {

    private final Object lock = new Object();

    private final AtomicReference<PendingReadOperation<?>> pendingReadOperation = new AtomicReference<>();

    private volatile boolean closed;

    private final ByteBuffer readBuffer;

    private final Consumer<Integer> listener;

    public Http2RequestBodyChannel(int readBufferSize, Consumer<Integer> listener) {
        this.readBuffer = ByteBuffer.allocate(readBufferSize).flip();
        this.listener = listener;
    }

    public void put(byte[] data) {
        synchronized (lock) {
            readBuffer.compact().put(data).flip();
        }
        transfer();
    }

    public void closeInbound() {
        closed = true;
    }

    @Override
    public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
        pendingReadOperation.set(new PendingReadOperation<>(dst, attachment, handler));
        transfer();
    }

    @Override
    public <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
        throw new UnsupportedOperationException();
    }

    private void transfer() {
        synchronized (lock) {
            if (closed && !readBuffer.hasRemaining()) {
                Run.async(() -> Optional.ofNullable(pendingReadOperation.getAndSet(null)).ifPresent(op -> op.complete(-1)));
                return;
            }
            if (pendingReadOperation.get() != null && readBuffer.hasRemaining()) {
                PendingReadOperation<?> readOp = pendingReadOperation.getAndSet(null);
                ByteBuffer dst = readOp.getDst();
                int counter = 0;
                while (dst.hasRemaining() && readBuffer.hasRemaining()) {
                    dst.put(readBuffer.get());
                    counter++;
                }
                listener.accept(counter);
                int result = counter;
                Run.async(() -> readOp.complete(result));
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
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        transfer();
    }

}
