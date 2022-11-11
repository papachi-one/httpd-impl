package one.papachi.httpd.impl.http;


import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.impl.Run;
import one.papachi.httpd.impl.net.GenericCompletionHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultHttpBody implements HttpBody {

    public static class DefaultBuilder implements Builder {

        private Object object;

        @Override
        public Builder setEmpty() {
            object = null;
            return this;
        }

        @Override
        public Builder setInput(AsynchronousByteChannel channel) {
            object = channel;
            return this;
        }

        @Override
        public Builder setInput(AsynchronousFileChannel channel) {
            object = channel;
            return this;
        }

        @Override
        public Builder setInput(ReadableByteChannel channel) {
            object = channel;
            return this;
        }

        @Override
        public Builder setInput(InputStream inputStream) {
            object = inputStream;
            return this;
        }

        @Override
        public HttpBody build() {
            return new DefaultHttpBody(object);
        }
    }

    private final Object input;

    private volatile boolean closed;

    protected final AtomicLong position = new AtomicLong();

    DefaultHttpBody(Object input) {
        this.input = input;
    }

    @Override
    public boolean isPresent() {
        return input != null;
    }

    @Override
    public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
        if (input == null) {
            Run.async(() -> handler.completed(-1, attachment));
        } else if (input instanceof AsynchronousByteChannel channel) {
            channel.read(dst, attachment, handler);
        } else if (input instanceof AsynchronousFileChannel channel) {
            channel.read(dst, position.get(), attachment, new CompletionHandler<>() {
                @Override
                public void completed(Integer result, A attachment) {
                    if (result > 0)
                        position.addAndGet(result);
                    handler.completed(result, attachment);
                }

                @Override
                public void failed(Throwable exc, A attachment) {
                    handler.failed(exc, attachment);
                }
            });
        } else if (input instanceof ReadableByteChannel channel) {
            // TODO async
            try {
                int result = channel.read(dst);
                handler.completed(result, attachment);
            } catch (IOException e) {
                handler.failed(e, attachment);
            }
        } else if (input instanceof InputStream inputStream) {
            // TODO async
            try {
                int result = inputStream.read(dst.array(), dst.arrayOffset() + dst.position(), dst.remaining());
                if (result == -1) {
                    handler.completed(result, attachment);
                    return;
                }
                dst.position(dst.position() + result);
                handler.completed(result, attachment);
            } catch (IOException e) {
                handler.failed(e, attachment);
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
        return isPresent() ? !closed : false;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        if (input instanceof AsynchronousByteChannel channel) {
            channel.close();
        } else if (input instanceof ReadableByteChannel channe) {
            channe.close();
        } else if (input instanceof InputStream inputStream) {
            inputStream.close();
        }
    }

    @Override
    public ReadableByteChannel getByteChannel() {
        if (input instanceof AsynchronousByteChannel channel) {
            return new ReadableByteChannel() {
                @Override
                public int read(ByteBuffer dst) throws IOException {
                    try {
                        return channel.read(dst).get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new IOException(e);
                    }
                }

                @Override
                public boolean isOpen() {
                    return channel.isOpen();
                }

                @Override
                public void close() throws IOException {
                    channel.close();
                }
            };
        } else if (input instanceof ReadableByteChannel channel) {
            return channel;
        } else if (input instanceof InputStream inputStream) {
            return new ReadableByteChannel() {
                private volatile boolean closed;
                @Override
                public int read(ByteBuffer dst) throws IOException {
                    return inputStream.read(dst.array(), dst.arrayOffset() + dst.position(), dst.remaining());
                }

                @Override
                public boolean isOpen() {
                    return !closed;
                }

                @Override
                public void close() throws IOException {
                    closed = true;
                    inputStream.close();
                }
            };
        }
        return null;
    }

    @Override
    public InputStream getInputStream() {
        if (input instanceof AsynchronousByteChannel channel) {
            return new InputStream() {
                private final ByteBuffer buffer = ByteBuffer.allocate(1);
                @Override
                public int read() throws IOException {
                    try {
                        Integer result = channel.read(buffer.clear()).get();
                        return result == -1 ? -1 : buffer.flip().get() & 0xFF;
                    } catch (ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        } else if (input instanceof ReadableByteChannel channel) {
            return new InputStream() {
                private final ByteBuffer buffer = ByteBuffer.allocate(1);
                @Override
                public int read() throws IOException {
                    int result = channel.read(buffer.clear());
                    return result == -1 ? -1 : buffer.flip().get() & 0xFF;
                }
            };
        } else if (input instanceof InputStream inputStream) {
            return inputStream;
        }
        return null;
    }

}
