package one.papachi.httpd.impl.net;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class AsynchronousBufferedSocketChannel extends AsynchronousSocketChannel {

    protected final AsynchronousSocketChannel channel;

    protected ByteBuffer readBuffer;

    public AsynchronousBufferedSocketChannel(AsynchronousSocketChannel channel, ByteBuffer buffer) {
        super(channel.provider());
        this.channel = channel;
        this.readBuffer = buffer;
    }

    public AsynchronousSocketChannel getChannel() {
        return channel;
    }

    public ByteBuffer getReadBuffer() {
        return readBuffer;
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public AsynchronousSocketChannel bind(SocketAddress local) throws IOException {
        return channel.bind(local);
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return channel.supportedOptions();
    }

    @Override
    public <T> T getOption(SocketOption<T> name) throws IOException {
        return channel.getOption(name);
    }

    @Override
    public <T> AsynchronousSocketChannel setOption(SocketOption<T> name, T value) throws IOException {
        return channel.setOption(name, value);
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return channel.getLocalAddress();
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        return channel.getRemoteAddress();
    }

    @Override
    public Future<Void> connect(SocketAddress remote) {
        return channel.connect(remote);
    }

    @Override
    public <A> void connect(SocketAddress remote, A attachment, CompletionHandler<Void, ? super A> handler) {
        channel.connect(remote, attachment, handler);
    }

    @Override
    public Future<Integer> read(ByteBuffer dst) {
        CompletableFuture<Integer> completableFuture = new CompletableFuture<>();
        read(dst, null, new GenericCompletionHandler<>(completableFuture));
        return completableFuture;
    }

    @Override
    public <A> void read(ByteBuffer dst, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler) {
        read(new ByteBuffer[]{dst}, 0, 1, timeout, unit, attachment, new CompletionHandler<>() {
            @Override
            public void completed(Long result, A attachment) {
                handler.completed(result.intValue(), attachment);
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                handler.failed(exc, attachment);
            }
        });
    }

    @Override
    public <A> void read(ByteBuffer[] dsts, int offset, int length, long timeout, TimeUnit unit, A attachment, CompletionHandler<Long, ? super A> handler) {
        if (readBuffer.hasRemaining()) {
            handler.completed(read0(dsts, offset, length), attachment);// TODO async
        } else {
            channel.read(readBuffer.clear(), timeout, unit, attachment, new CompletionHandler<>() {
                @Override
                public void completed(Integer result, A attachment) {
                    if (result == -1) {
                        handler.completed(-1L, attachment);
                        return;
                    }
                    handler.completed(read0(dsts, offset, length), attachment);
                }

                @Override
                public void failed(Throwable exc, A attachment) {
                    handler.failed(exc, attachment);
                }
            });
        }
    }

    protected long read0(ByteBuffer[] dsts, int offset, int length) {
        long result = 0L;
        for (int i = offset; i < length && readBuffer.hasRemaining(); i++) {
            ByteBuffer dst = dsts[i];
            while (readBuffer.hasRemaining() && dst.hasRemaining()) {
                dst.put(readBuffer.get());
                result++;
            }
        }
        return result;
    }

    @Override
    public Future<Integer> write(ByteBuffer src) {
        return channel.write(src);
    }


    @Override
    public <A> void write(ByteBuffer src, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler) {
        channel.write(src, timeout, unit, attachment, handler);
    }

    @Override
    public <A> void write(ByteBuffer[] srcs, int offset, int length, long timeout, TimeUnit unit, A attachment, CompletionHandler<Long, ? super A> handler) {
        channel.write(srcs, offset, length, timeout, unit, attachment, handler);
    }

    @Override
    public AsynchronousBufferedSocketChannel shutdownInput() throws IOException {
        channel.shutdownInput();
        return this;
    }

    @Override
    public AsynchronousBufferedSocketChannel shutdownOutput() throws IOException {
        channel.shutdownOutput();
        return this;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

}
