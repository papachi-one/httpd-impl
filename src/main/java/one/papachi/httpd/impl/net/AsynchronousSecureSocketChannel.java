package one.papachi.httpd.impl.net;

import one.papachi.httpd.impl.Run;

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
import java.nio.channels.ReadPendingException;
import java.nio.channels.WritePendingException;
import java.sql.Time;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AsynchronousSecureSocketChannel extends AsynchronousSocketChannel {

    protected final Object lockRead = new Object();

    protected final Object lockWrite = new Object();

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
            Run.async(() -> handler.failed(e, attachment));
            return;
        }
        doHandshake(attachment, handler);
    }

    protected <A> void doHandshake(A attachment, CompletionHandler<Void, ? super A> handler) {
            try {
                while (true) {
                    SSLEngineResult.HandshakeStatus handshakeStatus;
                    synchronized (sslEngine) {
                        handshakeStatus = sslEngine.getHandshakeStatus();
                    }
                    switch (handshakeStatus) {
                        case NOT_HANDSHAKING, FINISHED -> {
                            synchronized (lockRead) {
                                netInBuffer.compact().flip();
                            }
                            Run.async(() -> handler.completed(null, attachment));
                            return;
                        }
                        case NEED_TASK -> {
                            Runnable task;
                            while ((task = sslEngine.getDelegatedTask()) != null) {
                                task.run();
                            }
                        }
                        case NEED_UNWRAP, NEED_UNWRAP_AGAIN -> {
                            synchronized (lockRead) {
                                SSLEngineResult.Status status;
                                synchronized (sslEngine) {
                                    status = sslEngine.unwrap(netInBuffer, appInBuffer.compact()).getStatus();
                                }
                                appInBuffer.flip();
                                switch (status) {
                                    case OK -> {
                                    }
                                    case BUFFER_UNDERFLOW -> {
                                        channel.read(netInBuffer.compact(), attachment, new CompletionHandler<>() {
                                            @Override
                                            public void completed(Integer result, A attachment) {
                                                synchronized (lockRead) {
                                                    if (result == -1) {
                                                        Run.async(() -> handler.failed(new ClosedChannelException(), attachment));
                                                    } else {
                                                        counter.addAndGet(result);
                                                        netInBuffer.flip();
                                                        Run.async(() -> doHandshake(attachment, handler));
                                                    }
                                                }
                                            }

                                            @Override
                                            public void failed(Throwable exc, A attachment) {
                                                Run.async(() -> handler.failed(exc, attachment));
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
                        }
                        case NEED_WRAP -> {
                            synchronized (lockWrite) {
                                netOutBuffer.clear();
                                SSLEngineResult.Status status;
                                synchronized (sslEngine) {
                                    status = sslEngine.wrap(ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize()), netOutBuffer).getStatus();
                                }
                                switch (status) {
                                    case OK -> {
                                        netOutBuffer.flip();
                                        channel.write(netOutBuffer, attachment, new CompletionHandler<>() {
                                            @Override
                                            public void completed(Integer result, A attachment) {
                                                synchronized (lockRead) {
                                                    if (netOutBuffer.hasRemaining()) {
                                                        channel.write(netInBuffer, attachment, this);
                                                    } else {
                                                        Run.async(() -> doHandshake(attachment, handler));
                                                    }
                                                }
                                            }

                                            @Override
                                            public void failed(Throwable exc, A attachment) {
                                                Run.async(() -> handler.failed(exc, attachment));
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
                }
            } catch (Exception e) {
                Run.async(() -> handler.failed(e, attachment));
            }
    }

    @Override
    public Future<Integer> read(ByteBuffer dst) {
        CompletableFuture<Integer> completableFuture = new CompletableFuture<>();
        read(dst, null, new GenericCompletionHandler<>(completableFuture));
        return completableFuture;
    }

    AtomicBoolean isReading = new AtomicBoolean(false);

    AtomicLong counter = new AtomicLong(0);

    @Override
    public <A> void read(ByteBuffer dst, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler) {
        if (!isReading.compareAndSet(false, true)) {
            throw new ReadPendingException();
        }
        read0(dst, timeout, unit, attachment, handler);
    }

    <A> void read0(ByteBuffer dst, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler) {
        synchronized (lockRead) {
            if (appInBuffer.hasRemaining()) {
                int counter = 0;
                while (appInBuffer.hasRemaining() && dst.hasRemaining()) {
                    dst.put(appInBuffer.get());
                    counter++;
                }
                int result = counter;
                isReading.set(false);
                Run.async(() -> handler.completed(result, attachment));
            } else {
                SSLEngineResult.Status status;
                synchronized (sslEngine) {
                    try {
                        status = sslEngine.unwrap(netInBuffer, appInBuffer.compact()).getStatus();
                    } catch (SSLException e) {
                        System.err.println("ERROR counter = " + counter.get());
                        Run.async(() -> handler.failed(e, attachment));
                        return;
                    }
                }
                switch (status) {
                    case OK -> {
                        appInBuffer.flip();
                        read0(dst, timeout, unit, attachment, handler);
                    }
                    case BUFFER_UNDERFLOW -> {
                        appInBuffer.flip();
                        channel.read(netInBuffer.compact(), timeout, unit, attachment, new CompletionHandler<>() {
                            @Override
                            public void completed(Integer result, A attachment) {
                                synchronized (lockRead) {
                                    if (result == -1) {
                                        Run.async(() -> handler.completed(-1, attachment));
                                    } else {
                                        counter.addAndGet(result);
                                        netInBuffer.flip();
                                        read0(dst, timeout, unit, attachment, handler);
                                    }
                                }
                            }

                            @Override
                            public void failed(Throwable exc, A attachment) {
                                handler.failed(exc, attachment);
                            }
                        });
                    }
                    case BUFFER_OVERFLOW -> Run.async(() -> handler.failed(new SSLException("SSLEngine.unwrap(), Status.BUFFER_OVERFLOW"), attachment));
                    case CLOSED -> Run.async(() -> handler.completed(-1, attachment));
                }
            }
        }
    }

    @Override
    public <A> void read(ByteBuffer[] dsts, int offset, int length, long timeout, TimeUnit unit, A attachment, CompletionHandler<Long, ? super A> handler) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Future<Integer> write(ByteBuffer src) {
        CompletableFuture<Integer> completableFuture = new CompletableFuture<>();
        write(src, null, new GenericCompletionHandler<>(completableFuture));
        return completableFuture;
    }


    AtomicBoolean isWriting = new AtomicBoolean(false);

    @Override
    public <A> void write(ByteBuffer src, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler) {
        if (!isWriting.compareAndSet(false, true)) {
            throw new WritePendingException();
        }
        synchronized (lockWrite) {
            SSLEngineResult sslEngineResult;
            synchronized (sslEngine) {
                try {
                    sslEngineResult = sslEngine.wrap(src, netOutBuffer.clear());
                } catch (SSLException e) {
                    Run.async(() -> handler.failed(e, attachment));
                    return;
                }
            }
            switch (sslEngineResult.getStatus()) {
                case OK -> {
                    netOutBuffer.flip();
                    AtomicInteger resultInt = new AtomicInteger();
                    channel.write(netOutBuffer, timeout, unit, attachment, new CompletionHandler<>() {
                        @Override
                        public void completed(Integer result, A attachment) {
                            synchronized (lockWrite) {
                                resultInt.addAndGet(result);
                                if (netOutBuffer.hasRemaining()) {
                                    channel.write(netOutBuffer, timeout, unit, attachment, this);
                                } else {
                                    isWriting.set(false);
                                    Run.async(() -> handler.completed(resultInt.get(), attachment));
                                }
                            }
                        }

                        @Override
                        public void failed(Throwable exc, A attachment) {
                            handler.failed(exc, attachment);
                        }
                    });
                }
                case BUFFER_UNDERFLOW -> Run.async(() -> handler.failed(new SSLException("SSLEngine.wrap(), Status.BUFFER_UNDERFLOW"), attachment));
                case BUFFER_OVERFLOW -> Run.async(() -> handler.failed(new SSLException("SSLEngine.wrap(), Status.BUFFER_OVERFLOW"), attachment));
                case CLOSED -> Run.async(() -> handler.failed(new SSLException("SSLEngine.wrap(), Status.CLOSED"), attachment));
            }
        }
    }

    @Override
    public <A> void write(ByteBuffer[] srcs, int offset, int length, long timeout, TimeUnit unit, A attachment, CompletionHandler<Long, ? super A> handler) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public AsynchronousSecureSocketChannel shutdownInput() throws IOException {
        synchronized (lockRead) {
            sslEngine.closeInbound();
        }
        return this;
    }

    @Override
    public AsynchronousSecureSocketChannel shutdownOutput() throws IOException {
        synchronized (lockWrite) {
            try {
                sslEngine.closeOutbound();
                SSLEngineResult result = sslEngine.wrap(ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize()), netOutBuffer.clear());
                switch (result.getStatus()) {
                    case OK -> {
                        netOutBuffer.flip();
                        channel.write(netOutBuffer, null, new CompletionHandler<>() {
                            @Override
                            public void completed(Integer result, Object attachment) {
                                synchronized (lockWrite) {
                                    if (netOutBuffer.hasRemaining()) {
                                        channel.write(netOutBuffer, attachment, this);
                                    } else {
                                        try {
                                            channel.shutdownOutput();
                                        } catch (IOException e) {
                                        }
                                    }
                                }
                            }

                            @Override
                            public void failed(Throwable exc, Object attachment) {
                            }
                        });
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
    }

    @Override
    public void close() throws IOException {
        shutdownOutput();
        channel.close();
    }

}
