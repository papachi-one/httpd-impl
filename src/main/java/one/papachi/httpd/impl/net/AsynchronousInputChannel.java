package one.papachi.httpd.impl.net;

import one.papachi.httpd.impl.PendingReadOperation;
import one.papachi.httpd.impl.Run;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public class AsynchronousInputChannel implements AsynchronousByteChannel {

    protected final Object input;
    protected final AtomicLong counter = new AtomicLong();
    protected final long maxLength;
    protected volatile boolean isClosed;
    protected final CompletionHandler<Integer, PendingReadOperation<?>> completionHandler;

    public AsynchronousInputChannel(Object input) {
        this(input, Long.MAX_VALUE);
    }

    public AsynchronousInputChannel(Object input, Long maxLength) {
        this.input = input;
        this.maxLength = maxLength;
        this.isClosed = input == null;
        if (input != null && (input instanceof AsynchronousByteChannel || input instanceof AsynchronousFileChannel)) {
            completionHandler = new CompletionHandler<>() {
                @Override
                public void completed(Integer result, PendingReadOperation<?> attachment) {
                    if (result != -1) {
                        counter.addAndGet(result);
                        attachment.getDst().position(attachment.getDst().position() + result);
                    }
                    attachment.complete(result);
                }

                @Override
                public void failed(Throwable exc, PendingReadOperation<?> attachment) {
                    attachment.fail(exc);
                }
            };
        } else {
            completionHandler = null;
        }
    }

    @Override
    public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
        if (input == null || maxLength == counter.get()) {
            isClosed = true;
            Run.async(() -> handler.completed(-1, attachment));
        } else if (input instanceof AsynchronousByteChannel channel) {
            int length = (int) Math.min(maxLength - counter.get(), dst.remaining());
            ByteBuffer duplicate = dst.duplicate();
            duplicate.limit(duplicate.position() + length);
            channel.read(duplicate, new PendingReadOperation<>(dst, attachment, handler), completionHandler);
        } else if (input instanceof AsynchronousFileChannel channel) {
            int length = (int) Math.min(maxLength - counter.get(), dst.remaining());
            ByteBuffer duplicate = dst.duplicate();
            duplicate.limit(duplicate.position() + length);
            channel.read(duplicate, counter.get(), new PendingReadOperation<>(dst, attachment, handler), completionHandler);
        } else if (input instanceof ReadableByteChannel channel) {
            Run.async(() -> {
                try {
                    int length = (int) Math.min(maxLength - counter.get(), dst.remaining());
                    ByteBuffer duplicate = dst.duplicate();
                    duplicate.limit(duplicate.position() + length);
                    int result = channel.read(duplicate);
                    if (result != -1) {
                        counter.addAndGet(result);
                        dst.position(dst.position() + result);
                    }
                    handler.completed(result, attachment);
                } catch (IOException e) {
                    handler.failed(e, attachment);
                }
            });
        } else if (input instanceof InputStream inputStream) {
            Run.async(() -> {
                try {
                    int length = (int) Math.min(maxLength - counter.get(), dst.remaining());
                    int result = inputStream.read(dst.array(), dst.arrayOffset() + dst.position(), length);
                    if (result != -1) {
                        counter.addAndGet(result);
                        dst.position(dst.position() + result);
                    }
                    handler.completed(result, attachment);
                } catch (IOException e) {
                    handler.failed(e, attachment);
                }
            });
        } else {
            throw new NullPointerException("input is null");
        }
    }

    @Override
    public Future<Integer> read(ByteBuffer dst) {
        CompletableFuture<Integer> completableFuture = new CompletableFuture<>();
        read(dst, null, new GenericCompletionHandler<>(completableFuture));
        return completableFuture;
    }

    @Override
    public <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Future<Integer> write(ByteBuffer src) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean isOpen() {
        return !isClosed;
    }

    @Override
    public void close() throws IOException {
        isClosed = true;
        if (input != null) {
            if (input instanceof AsynchronousByteChannel channel) {
                channel.close();
            } else if (input instanceof AsynchronousFileChannel channel) {
                channel.close();
            } else if (input instanceof ReadableByteChannel channel) {
                channel.close();
            } else if (input instanceof InputStream inputStream) {
                inputStream.close();
            }
        }
    }

}
