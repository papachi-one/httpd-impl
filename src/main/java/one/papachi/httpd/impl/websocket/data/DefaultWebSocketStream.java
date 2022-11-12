package one.papachi.httpd.impl.websocket.data;

import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.websocket.WebSocketStream;
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
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultWebSocketStream implements WebSocketStream {

    public static class DefaultBuilder implements Builder {

        private Object object;

        @Override
        public Builder input(AsynchronousByteChannel channel) {
            object = channel;
            return this;
        }

        @Override
        public Builder input(AsynchronousFileChannel channel) {
            object = channel;
            return this;
        }

        @Override
        public Builder input(ReadableByteChannel channel) {
            object = channel;
            return this;
        }

        @Override
        public Builder input(InputStream inputStream) {
            object = inputStream;
            return this;
        }

        @Override
        public WebSocketStream build() {
            return null;
        }
    }

    private final Object input;

    private volatile boolean closed;

    protected final AtomicLong position = new AtomicLong();

    DefaultWebSocketStream(Object input) {
        this.input = input;
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
            Run.async(() -> {
                try {
                    handler.completed(channel.read(dst), attachment);
                } catch (IOException e) {
                    handler.failed(e, attachment);
                }
            });
        } else if (input instanceof InputStream inputStream) {
            Run.async(() -> {
                try {
                    int result = inputStream.read(dst.array(), dst.arrayOffset() + dst.position(), dst.remaining());
                    if (result != -1) {
                        dst.position(dst.position() + result);
                    }
                    handler.completed(result, attachment);
                } catch (IOException e) {
                    handler.failed(e, attachment);
                }
            });
        } else {
            throw new IllegalStateException();
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
        return !closed;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        if (input instanceof AsynchronousByteChannel channel) {
            channel.close();
        } else if (input instanceof ReadableByteChannel channel) {
            channel.close();
        } else if (input instanceof InputStream inputStream) {
            inputStream.close();
        }
    }
}
