package one.papachi.httpd.impl.http;


import one.papachi.httpd.impl.Run;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class Http2LocalBodyChannel {

    private final Object lock = new Object();
    private final AsynchronousByteChannel channel;

    private final Consumer<Http2LocalBodyChannel> listener;

    private final ByteBuffer readBuffer;

    private final ByteBuffer writeBuffer;

    private volatile boolean isClosed;

    private final AtomicLong counter = new AtomicLong();

    private final AtomicLong counterRead = new AtomicLong();

    private final AtomicBoolean amIReading = new AtomicBoolean();

    public Http2LocalBodyChannel(AsynchronousByteChannel channel, int bufferSize, Consumer<Http2LocalBodyChannel> listener) {
        this.channel = channel;
        this.listener = listener;
        this.readBuffer = ByteBuffer.allocate(bufferSize).flip();
        this.writeBuffer = readBuffer.duplicate().clear();
    }

    public void start() {
        Run.async(this::readFromChannel);
    }

    private void readFromChannel() {
        if (!amIReading.compareAndSet(false, true))
            return;
        channel.read(writeBuffer, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                if (result == -1) {
                    isClosed = true;
                    Run.async(() -> listener.accept(Http2LocalBodyChannel.this));
                    return;
                }
                counter.addAndGet(result);
                synchronized (lock) {
                    readBuffer.limit(writeBuffer.position());
                    Run.async(() -> listener.accept(Http2LocalBodyChannel.this));
                }
                if (writeBuffer.hasRemaining()) {
                    channel.read(writeBuffer, attachment, this);
                } else {
                    amIReading.set(false);
                }
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                exc.printStackTrace();
                isClosed = true;
            }
        });
    }

    public record ReadResult(int result, ByteBuffer buffer) {}

    public ReadResult read(int maxSize) {
        synchronized (lock) {
            if (isClosed && !readBuffer.hasRemaining()) {
                return new ReadResult(-1, null);
            }
            int min = Math.min(readBuffer.remaining(), maxSize);
            ByteBuffer duplicate = readBuffer.duplicate();
            duplicate.limit(duplicate.position() + min);
            counterRead.addAndGet(min);
            return new ReadResult(min, duplicate);
        }
    }

    public void release(int min) {
        synchronized (lock) {
            readBuffer.position(readBuffer.position() + min);
            if (amIReading.compareAndSet(false, true)) {
                readBuffer.compact().flip();
                writeBuffer.position(readBuffer.limit());
                amIReading.set(false);
                Run.async(this::readFromChannel);
            }
            if (readBuffer.hasRemaining() || isClosed) {
                Run.async(() -> listener.accept(Http2LocalBodyChannel.this));
            }
        }
    }

}
