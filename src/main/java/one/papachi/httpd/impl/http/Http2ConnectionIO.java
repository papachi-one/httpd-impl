package one.papachi.httpd.impl.http;


import one.papachi.httpd.impl.CustomDataBuffer;
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
        READ, READ_MAGIC, READ_FRAME_HEADER, READ_FRAME_PAYLOAD, PROCESS_FRAME, ERROR, BREAK
    }

    protected final AsynchronousSocketChannel channel;
    protected final ByteBuffer readBuffer;
    protected final Function<Http2Frame, State> listener;
    protected final int maxFrameSize;
    protected volatile State state, resumeState;
    protected volatile CustomDataBuffer osBuffer;
    protected volatile int length, counter;

    private volatile Http2FrameHeader frameHeader;
    private volatile Http2Frame frame;

    private record WriteOperation(ByteBuffer buffer, Runnable runnable) {
    }

    protected final Queue<WriteOperation> writeQueue = new LinkedList<>();

    protected final AtomicReference<Boolean> amIWriting = new AtomicReference<>(false);

    public Http2ConnectionIO(Mode mode, AsynchronousSocketChannel channel, Http2Settings localSettings, Function<Http2Frame, State> listener) {
        this.channel = channel;
        this.readBuffer = ByteBuffer.allocate(localSettings.getMaxFrameSize()).flip();
        this.listener = listener;
        this.maxFrameSize = localSettings.getMaxFrameSize();
        run(mode == Mode.SERVER ? State.READ_MAGIC : State.READ_FRAME_HEADER);
    }

    public ByteBuffer getReadBuffer() {
        return readBuffer;
    }

    private void run(State state) {
        this.state = state;
        run();
    }

    @Override
    public void run() {
        while (true) {
            if (state == State.READ) {
                if (readBuffer.hasRemaining()) {
                    state = resumeState;
                } else {
                    state = State.BREAK;
                    read();
                    break;
                }
            } else if (state == State.READ_MAGIC) {
                state = readMagic();
            } else if (state == State.READ_FRAME_HEADER) {
                state = readFrameHeader();
            } else if (state == State.READ_FRAME_PAYLOAD) {
                state = readFramePayload();
            } else if (state == State.PROCESS_FRAME) {
                state = processFrame();
            } else if (state == State.ERROR) {
                state = close();
            }
            if (state == State.BREAK)
                break;
        }
    }

    private State read() {
        channel.read(readBuffer.compact(), null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                if (result == -1) {
                    run(State.ERROR);
                    return;
                }
                readBuffer.flip();
                run(resumeState);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                exc.printStackTrace();
                run(State.ERROR);
            }
        });
        return State.BREAK;
    }

    private State readMagic() {
        if (osBuffer == null) {
            counter = 0;
            length = 24;
            osBuffer = new CustomDataBuffer(length);
        }
        while (counter < length && readBuffer.hasRemaining()) {
            counter++;
            byte b = readBuffer.get();
            osBuffer.write(b);
        }
        if (counter == length) {
            boolean equals = osBuffer.toString().equals("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n");
            osBuffer = null;
            if (!equals) {
                return State.ERROR;
            }
            return State.READ_FRAME_HEADER;
        }
        resumeState = State.READ_MAGIC;
        return State.READ;
    }

    private State readFrameHeader() {
        if (osBuffer == null) {
            counter = 0;
            length = 9;
            osBuffer = new CustomDataBuffer(length);
        }
        while (counter < length && readBuffer.hasRemaining()) {
            counter++;
            byte b = readBuffer.get();
            osBuffer.write(b);
        }
        if (counter == length) {
            byte[] data = osBuffer.getArray();
            osBuffer = null;
            frameHeader = new Http2FrameHeader(data);
            length = frameHeader.getLength();
            if (length > maxFrameSize) {
                return State.ERROR;// error = Http2Error.FRAME_SIZE_ERROR;
            }
            return State.READ_FRAME_PAYLOAD;
        }
        resumeState = State.READ_FRAME_HEADER;
        return State.READ;
    }

    private State readFramePayload() {
        if (osBuffer == null) {
            counter = 0;
            osBuffer = new CustomDataBuffer(length);
        }
        while (counter < length && readBuffer.hasRemaining()) {
            counter++;
            byte b = readBuffer.get();
            osBuffer.write(b);
        }
        if (counter == length) {
            byte[] framePayload = osBuffer.getArray();
            osBuffer = null;
            frame = new Http2Frame(frameHeader, framePayload);
            return State.PROCESS_FRAME;
        }
        resumeState = State.READ_FRAME_PAYLOAD;
        return State.READ;
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
