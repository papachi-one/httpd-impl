package one.papachi.httpd.impl.net;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class AsynchronousSecureSocketChannel extends AsynchronousSocketChannel {

    protected final AsynchronousSocketChannel channel;

    protected final SSLEngine sslEngine;

    protected final ByteBuffer appInBuffer;

    protected final ByteBuffer netInBuffer;

    protected final ByteBuffer netOutBuffer;

    public AsynchronousSecureSocketChannel(AsynchronousSocketChannel channel, SSLEngine sslEngine) {
        super(channel.provider());
        this.channel = channel;
        this.sslEngine = sslEngine;
        appInBuffer = ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize());
        netInBuffer = ByteBuffer.allocate(sslEngine.getSession().getPacketBufferSize());
        netOutBuffer = ByteBuffer.allocate(sslEngine.getSession().getPacketBufferSize());
        netInBuffer.flip();
        appInBuffer.flip();
    }

    public SSLEngine getSslEngine() {
        return sslEngine;
    }

    public AsynchronousSocketChannel getChannel() {
        return channel;
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
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        connect(remote, null, new GenericCompletionHandler<>(completableFuture));
        return completableFuture;
    }

    @Override
    public <A> void connect(SocketAddress remote, A attachment, CompletionHandler<Void, ? super A> handler) {
        channel.connect(remote, attachment, handler);
    }

    public Future<Void> handshake() {
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        handshake(null, new GenericCompletionHandler<>(completableFuture));
        return completableFuture;
    }

    public <A> void handshake(A attachment, CompletionHandler<Void, ? super A> handler) {
        try {
            sslEngine.beginHandshake();
        } catch (Exception e) {
            handler.failed(e, attachment);// TODO async
            return;
        }
        doHandshake(attachment, handler);
    }

    protected <A> void doHandshake(A attachment, CompletionHandler<Void, ? super A> handler) {
        try {
            while (true) {
                switch (sslEngine.getHandshakeStatus()) {
                    case NOT_HANDSHAKING, FINISHED -> {
                        netInBuffer.clear().flip();
                        handler.completed(null, attachment);
                        return;
                    }
                    case NEED_TASK -> {
                        Runnable task;
                        while ((task = sslEngine.getDelegatedTask()) != null) {
                            task.run();
                        }
                    }
                    case NEED_UNWRAP, NEED_UNWRAP_AGAIN -> {
                        switch (sslEngine.unwrap(netInBuffer, ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize())).getStatus()) {
                            case OK -> {
                            }
                            case BUFFER_UNDERFLOW -> {
                                channel.read(netInBuffer.compact(), attachment, new CompletionHandler<>() {
                                    @Override
                                    public void completed(Integer result, A attachment) {
                                        if (result == -1) {
                                            handler.failed(new ClosedChannelException(), attachment);
                                        } else {
                                            netInBuffer.flip();
                                            doHandshake(attachment, handler);
                                        }
                                    }

                                    @Override
                                    public void failed(Throwable exc, A attachment) {
                                        handler.failed(exc, attachment);
                                    }
                                });
                                return;
                            }
                            case BUFFER_OVERFLOW -> {
                                throw new SSLException("HandshakeStatus.NEED_UNWRAP, Status.BUFFER_OVERFLOW");
                            }
                            case CLOSED -> {
                                throw new SSLException("HandshakeStatus.NEED_UNWRAP, Status.CLOSED");
                            }
                        }
                    }
                    case NEED_WRAP -> {
                        switch (sslEngine.wrap(ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize()), netOutBuffer.clear()).getStatus()) {
                            case OK -> {
                                netOutBuffer.flip();
                                channel.write(netOutBuffer, attachment, new CompletionHandler<>() {
                                    @Override
                                    public void completed(Integer result, A attachment) {
                                        if (netOutBuffer.hasRemaining()) {
                                            channel.write(netInBuffer, attachment, this);
                                        } else {
                                            doHandshake(attachment, handler);
                                        }
                                    }

                                    @Override
                                    public void failed(Throwable exc, A attachment) {
                                        handler.failed(exc, attachment);
                                    }
                                });
                                return;
                            }
                            case BUFFER_UNDERFLOW -> {
                                throw new SSLException("HandshakeStatus.NEED_WRAP, Status.BUFFER_UNDERFLOW");
                            }
                            case BUFFER_OVERFLOW -> {
                                throw new SSLException("HandshakeStatus.NEED_WRAP, Status.BUFFER_OVERFLOW");
                            }
                            case CLOSED -> {
                                throw new SSLException("HandshakeStatus.NEED_WRAP, Status.CLOSED");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            handler.failed(e, attachment);// TODO async
        }
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
        if (appInBuffer.hasRemaining()) {
            long result = 0L;
            for (int i = offset; i < length && appInBuffer.hasRemaining(); i++) {
                ByteBuffer dst = dsts[i];
                while (appInBuffer.hasRemaining() && dst.hasRemaining()) {
                    dst.put(appInBuffer.get());
                    result++;
                }
            }
            handler.completed(result, attachment);// TODO async
        } else {
            try {
                switch (sslEngine.unwrap(netInBuffer, appInBuffer.clear()).getStatus()) {
                    case OK -> {
                        appInBuffer.flip();
                        read(dsts, offset, length, timeout, unit, attachment, handler);
                    }
                    case BUFFER_UNDERFLOW -> {
                        appInBuffer.flip();
                        channel.read(netInBuffer.compact(), timeout, unit, attachment, new CompletionHandler<>() {
                            @Override
                            public void completed(Integer result, A attachment) {
                                if (result == -1) {
                                    handler.completed(-1L, attachment);
                                } else {
                                    netInBuffer.flip();
                                    read(dsts, offset, length, timeout, unit, attachment, handler);
                                }
                            }

                            @Override
                            public void failed(Throwable exc, A attachment) {
                                handler.failed(exc, attachment);
                            }
                        });
                    }
                    case BUFFER_OVERFLOW -> {
                        throw new SSLException("SSLEngine.unwrap(), Status.BUFFER_OVERFLOW");
                    }
                    case CLOSED -> {
                    }
                }
            } catch (Exception e) {
                handler.failed(e, attachment);// TODO async
            }
        }
    }

    @Override
    public Future<Integer> write(ByteBuffer src) {
        CompletableFuture<Integer> completableFuture = new CompletableFuture<>();
        write(src, null, new GenericCompletionHandler<>(completableFuture));
        return completableFuture;
    }


    @Override
    public <A> void write(ByteBuffer src, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler) {
        write(new ByteBuffer[]{src}, 0, 1, timeout, unit, attachment, new CompletionHandler<>() {
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
    public <A> void write(ByteBuffer[] srcs, int offset, int length, long timeout, TimeUnit unit, A attachment, CompletionHandler<Long, ? super A> handler) {
        try {
            SSLEngineResult sslEngineResult = sslEngine.wrap(srcs, offset, length, netOutBuffer.clear());
            switch (sslEngineResult.getStatus()) {
                case OK -> {
                    netOutBuffer.flip();
                    AtomicLong resultLong = new AtomicLong();
                    channel.write(netOutBuffer, timeout, unit, attachment, new CompletionHandler<>() {
                        @Override
                        public void completed(Integer result, A attachment) {
                            resultLong.addAndGet((long) result);
                            if (netOutBuffer.hasRemaining()) {
                                channel.write(netOutBuffer, timeout, unit, attachment, this);
                            } else {
                                handler.completed(resultLong.get(), attachment);
                            }
                        }

                        @Override
                        public void failed(Throwable exc, A attachment) {
                            handler.failed(exc, attachment);
                        }
                    });
                }
                case BUFFER_UNDERFLOW -> throw new SSLException("SSLEngine.wrap(), Status.BUFFER_UNDERFLOW");
                case BUFFER_OVERFLOW -> throw new SSLException("SSLEngine.wrap(), Status.BUFFER_OVERFLOW");
                case CLOSED -> handler.completed((long) sslEngineResult.bytesConsumed(), attachment);
            }
        } catch (Exception e) {
            handler.failed(e, attachment);// TODO async
        }
    }

    @Override
    public AsynchronousSecureSocketChannel shutdownInput() throws IOException {
        sslEngine.closeInbound();
        return this;
    }

    @Override
    public AsynchronousSecureSocketChannel shutdownOutput() throws IOException {
        try {
            sslEngine.closeOutbound();
            SSLEngineResult result = sslEngine.wrap(ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize()), netOutBuffer.clear());
            switch (result.getStatus()) {
                case OK -> {
                    netOutBuffer.flip();
                    while (netOutBuffer.hasRemaining())
                        channel.write(netOutBuffer).get();
                    channel.shutdownOutput();
                }
                case BUFFER_UNDERFLOW -> throw new SSLException("SSLEngine.wrap(), Status.BUFFER_UNDERFLOW");
                case BUFFER_OVERFLOW -> throw new SSLException("SSLEngine.wrap(), Status.BUFFER_OVERFLOW");
                case CLOSED -> channel.shutdownOutput();
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
        return this;
    }

    @Override
    public void close() throws IOException {
        shutdownOutput();
        channel.close();
    }

}
