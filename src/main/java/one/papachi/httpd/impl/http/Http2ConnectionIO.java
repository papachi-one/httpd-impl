package one.papachi.httpd.impl.http;


import one.papachi.httpd.impl.Run;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class Http2ConnectionIO implements Runnable {

    public enum Mode {
        CLIENT, SERVER
    }

    public enum State {
        READ_MORE, READ_MAGIC, READ_FRAME_HEADER, READ_FRAME_PAYLOAD, PROCESS_FRAME, ERROR, BREAK
    }

    protected final Object lock = new Object();
    protected final AsynchronousSocketChannel channel;
    protected volatile ByteBuffer readBuffer;
    protected final Function<Http2Frame, State> listener;
    protected final Http2Settings localSettings;
    protected volatile State state, resumeState;
    protected volatile int length;

    private volatile Http2FrameHeader frameHeader;
    private volatile Http2Frame frame;

    private record WriteOperation(ByteBuffer buffer, Runnable runnable) {
    }

    protected final Queue<WriteOperation> writeQueue = new LinkedList<>();

    protected final AtomicReference<Boolean> amIWriting = new AtomicReference<>(false);

    public Http2ConnectionIO(Mode mode, AsynchronousSocketChannel channel, Http2Settings localSettings, Function<Http2Frame, State> listener) {
        this.channel = channel;
        this.readBuffer = ByteBuffer.allocate(localSettings.getMaxFrameSize()).flip();
        this.localSettings = localSettings;
        this.listener = listener;
    }

    public ByteBuffer getReadBuffer() {
        return readBuffer;
    }

    public void run(State state) {
        synchronized (lock) {
            this.state = state;
        }
        run();
    }

    @Override
    public void run() {
        synchronized (lock) {
            while (true) {
                if (state == State.READ_MORE) {
                    state = State.BREAK;
                    readBuffer.compact();
                    Run.async(() -> read());
                    break;
                } else if (state == State.READ_MAGIC) {
                    state = readMagic();
                } else if (state == State.READ_FRAME_HEADER) {
                    state = readFrameHeader();
                } else if (state == State.READ_FRAME_PAYLOAD) {
                    state = readFramePayload();
                } else if (state == State.PROCESS_FRAME) {
                    state = processFrame();
                    if (localSettings.getMaxFrameSize() > readBuffer.capacity()) {
                        ByteBuffer buffer = ByteBuffer.allocate(localSettings.getMaxFrameSize());
                        buffer.put(readBuffer).flip();
                        readBuffer = buffer;
                    }
                } else if (state == State.ERROR) {
                    state = close();
                }
                if (state == State.BREAK)
                    break;
            }
        }
    }

    private void read() {
        channel.read(readBuffer, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                synchronized (lock) {
                    if (result == -1) {
                        run(State.ERROR);
                        return;
                    }
                    readBuffer.flip();
                    run(resumeState);
                }
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                exc.printStackTrace();
                run(State.ERROR);
            }
        });
    }

    private State readMagic() {
        if (readBuffer.remaining() < 24) {
            resumeState = State.READ_MAGIC;
            return State.READ_MORE;
        } else {
            String magicString = new String(readBuffer.array(), readBuffer.arrayOffset() + readBuffer.position(), 24);
            readBuffer.position(readBuffer.position() + 24);
            boolean equals = magicString.equals("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n");
            if (!equals) {
                return State.ERROR;
            }
            return State.READ_FRAME_HEADER;
        }
    }

    private State readFrameHeader() {
        if (readBuffer.remaining() < 9) {
            resumeState = State.READ_FRAME_HEADER;
            return State.READ_MORE;
        } else {
            ByteBuffer buffer = readBuffer.duplicate();
            buffer.limit(buffer.position() + 9);
            readBuffer.position(readBuffer.position() + 9);
            frameHeader = new Http2FrameHeader(buffer);
            length = frameHeader.getLength();
            if (length > localSettings.getMaxFrameSize()) {
                return State.ERROR;// error = Http2Error.FRAME_SIZE_ERROR;
            }
            return State.READ_FRAME_PAYLOAD;
        }
    }

    private State readFramePayload() {
        if (readBuffer.remaining() < length) {
            resumeState = State.READ_FRAME_PAYLOAD;
            return State.READ_MORE;
        } else {
            ByteBuffer buffer = readBuffer.duplicate();
            buffer.limit(buffer.position() + length);
            readBuffer.position(readBuffer.position() + length);
            frame = new Http2Frame(frameHeader, buffer);
            return State.PROCESS_FRAME;
        }
    }

    private State processFrame() {
        return listener.apply(frame);
    }

    private State close() {
        return State.BREAK;
    }

    public void write(ByteBuffer... buffers) {
        write(Arrays.asList(buffers));
    }

    public void write(List<ByteBuffer> buffers) {
        write(null, buffers);
    }

    public void write(Runnable runnable, ByteBuffer... buffers) {
        write(runnable, Arrays.asList(buffers));
    }

    public void write(Runnable runnable, List<ByteBuffer> buffers) {
        synchronized (writeQueue) {
            Iterator<ByteBuffer> iterator = buffers.iterator();
            while (iterator.hasNext()) {
                ByteBuffer buffer = iterator.next();
                writeQueue.offer(new WriteOperation(buffer, null));
            }
            if (runnable != null) {
                writeQueue.offer(new WriteOperation(null, runnable));
            }
            write();
        }
    }

    private void write() {
        synchronized (writeQueue) {
            if (!writeQueue.isEmpty() && amIWriting.compareAndSet(false, true)) {
                WriteOperation writeOperation = writeQueue.poll();
                if (writeOperation.buffer != null) {
                    channel.write(writeOperation.buffer, null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer result, Void attachment) {
                            if (writeOperation.buffer.hasRemaining()) {
                                channel.write(writeOperation.buffer, attachment, this);
                            } else {
                                Optional.ofNullable(writeOperation.runnable).ifPresent(Run::async);
                                amIWriting.set(false);
                                write();
                            }
                        }

                        @Override
                        public void failed(Throwable exc, Void attachment) {
                        }
                    });
                } else {
                    Optional.ofNullable(writeOperation.runnable).ifPresent(Run::async);
                    amIWriting.set(false);
                    write();
                }
            }
        }
    }

}
