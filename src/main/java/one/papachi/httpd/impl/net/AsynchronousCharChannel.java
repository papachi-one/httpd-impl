package one.papachi.httpd.impl.net;

import one.papachi.httpd.impl.Run;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class AsynchronousCharChannel implements AsynchronousChannel {

    protected final AsynchronousByteChannel channel;

    protected final Charset charset;

    protected final CharsetDecoder decoder;

    protected final ByteBuffer buffer;

    protected volatile boolean isEos;

    public AsynchronousCharChannel(AsynchronousByteChannel channel, Charset charset) {
        this.channel = channel;
        this.charset = charset;
        this.decoder = charset.newDecoder();
        this.buffer = ByteBuffer.allocate(8 * 1024);
    }

    public Future<Integer> read(CharBuffer dst) {
        CompletableFuture<Integer> completableFuture = new CompletableFuture<>();
        read(dst, null, new GenericCompletionHandler<>(completableFuture));
        return completableFuture;
    }

    public <A> void read(CharBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
        synchronized (buffer) {
            if (buffer.hasRemaining()) {
                CoderResult result = decoder.decode(buffer, dst, isEos);
                if (result.isError()) {
                    try {
                        result.throwException();
                    } catch (Exception e) {
                        Run.async(() -> handler.failed(e, attachment));
                        return;
                    }
                }
                Run.async(() -> handler.completed(result.length(), attachment));
            } else if (isEos) {
                Run.async(() -> handler.completed(-1, attachment));
            } else {
                channel.read(buffer.compact(), attachment, new CompletionHandler<Integer, A>() {
                    @Override
                    public void completed(Integer result, A attachment) {
                        isEos = result == -1;
                        synchronized (buffer) {
                            buffer.flip();
                            read(dst, attachment, handler);
                        }
                    }

                    @Override
                    public void failed(Throwable exc, A attachment) {
                        handler.failed(exc, attachment);
                    }
                });
            }
        }
    }


    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public void close() throws IOException {

    }

}
