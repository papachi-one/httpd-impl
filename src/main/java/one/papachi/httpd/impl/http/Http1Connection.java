package one.papachi.httpd.impl.http;


import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpHeaders;
import one.papachi.httpd.impl.CustomDataBuffer;
import one.papachi.httpd.impl.Run;
import one.papachi.httpd.impl.StandardHttpOptions;
import one.papachi.httpd.impl.http.data.DefaultHttpHeaders;
import one.papachi.httpd.impl.net.TransferAsynchronousByteChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class Http1Connection implements Runnable {

    protected enum State {
        READ, READ_CHUNK_SIZE, READ_REMOTE, READ_REMOTE_LINE, READ_REMOTE_HEADER_LINE, READ_REMOTE_BODY, PROCESS, BREAK, ERROR,
    }

    protected static final byte[] CRLF = new byte[]{'\r', '\n'};

    protected static final byte[] ZERO_CRLF_CRLF = new byte[]{'0', '\r', '\n', '\r', '\n'};

    protected static final byte[] CRLF_ZERO_CRLF_CRLF = new byte[]{'\r', '\n', '0', '\r', '\n', '\r', '\n'};

    protected final Object lock = new Object();
    protected final AsynchronousSocketChannel channel;
    protected final ByteBuffer readBuffer;
    protected volatile ByteBuffer writeBuffer;
    protected volatile State state = State.READ_REMOTE, resumeState;
    protected volatile CustomDataBuffer osBuffer;
    protected volatile boolean isChunked;
    protected volatile boolean isEos;
    protected volatile long length, counter;
    protected volatile String remoteLine;
    protected volatile HttpHeaders.Builder remoteHeadersBuilder;
    protected volatile HttpHeaders remoteHeaders;
    protected volatile TransferAsynchronousByteChannel remoteBodyChannel;
    protected volatile HttpBody remoteBody;
    protected volatile boolean isLocalBodyChunked;
    protected volatile long localBodyLengthCounter;
    protected volatile boolean shutdownOutboundAfterBody;
    protected volatile boolean readRemoteBodyUntilEos;
    protected volatile boolean isError;

    public Http1Connection(AsynchronousSocketChannel channel) {
        this.channel = channel;
        this.readBuffer = ByteBuffer.allocate(StandardHttpOptions.READ_BUFFER_SIZE.defaultValue()).flip();
    }

    protected void run(State state) {
        synchronized (lock) {
            this.state = state;
        }
        run();
    }

    @Override
    public void run() {
        synchronized (lock) {
            while (true) {
                if (state == State.READ) {
                    if (readBuffer.hasRemaining()) {
                        state = isChunked ? State.READ_CHUNK_SIZE : resumeState;
                    } else {
                        state = State.BREAK;
                        Run.async(() -> read());
                        break;
                    }
                } else if (state == State.READ_CHUNK_SIZE) {
                    state = readChunkSize();
                } else if (state == State.READ_REMOTE) {
                    state = readRemote();
                } else if (state == State.READ_REMOTE_LINE) {
                    state = readRemoteLine();
                } else if (state == State.READ_REMOTE_HEADER_LINE) {
                    state = readRemoteHeaderLine();
                } else if (state == State.PROCESS) {
                    state = State.BREAK;
                    processRemote();
                } else if (state == State.READ_REMOTE_BODY) {
                    state = readRemoteBody();
                } else if (state == State.ERROR) {
                    state = closeConnection();
                }
                if (state == State.BREAK)
                    break;
            }
        }
    }

    protected final CompletionHandler<Integer, Void> read = new CompletionHandler<>() {
        @Override
        public void completed(Integer result, Void attachment) {
            synchronized (lock) {
                readBuffer.flip();
                if (result == -1) {
                    isEos = true;
                    run(resumeState);
                    return;
                }
                run(isChunked ? State.READ_CHUNK_SIZE : resumeState);
            }
        }

        @Override
        public void failed(Throwable exc, Void attachment) {
            synchronized (lock) {
                run(State.ERROR);
            }
        }
    };

    protected void read() {
        channel.read(readBuffer.compact(), null, read);
    }

    protected State readChunkSize() {
        if (counter < length) {
            return resumeState;
        }
        if (osBuffer == null) {
            osBuffer = new CustomDataBuffer();
        }
        while (readBuffer.hasRemaining()) {
            byte b = readBuffer.get();
            if (b == '\r') continue;
            if (b == '\n') {
                String line = osBuffer.toString(StandardCharsets.US_ASCII);
                osBuffer = null;
                if (line.isEmpty()) {
                    if (length == 0) {
                        isEos = true;
                        return resumeState;
                    }
                    return State.READ_CHUNK_SIZE;
                }
                length = Long.parseLong(line, 16);
                counter = 0;
                if (length == 0) return State.READ_CHUNK_SIZE;
                return resumeState;
            }
            osBuffer.write(b);
        }
        return State.READ;
    }

    protected State readRemote() {
        writeBuffer = null;
        osBuffer = null;
        isChunked = false;
        length = counter = 0L;
        remoteLine = null;
        remoteHeadersBuilder = new DefaultHttpHeaders.DefaultBuilder();
        remoteBodyChannel = null;
        return State.READ_REMOTE_LINE;
    }

    protected State readRemoteLine() {
        if (osBuffer == null) {
            osBuffer = new CustomDataBuffer();
        }
        while (readBuffer.hasRemaining()) {
            byte b = readBuffer.get();
            if (b == '\r') continue;
            if (b == '\n') {
                this.remoteLine = osBuffer.toString(StandardCharsets.US_ASCII);
                osBuffer = null;
                return State.READ_REMOTE_HEADER_LINE;
            }
            osBuffer.write(b);
            if (osBuffer.getLength() > StandardHttpOptions.REQUEST_LINE_MAX_LENGTH.defaultValue()) {
                return State.ERROR;
            }
        }
        if (isEos) {
            return State.ERROR;
        }
        resumeState = State.READ_REMOTE_LINE;
        return State.READ;
    }

    protected State readRemoteHeaderLine() {
        if (osBuffer == null) {
            osBuffer = new CustomDataBuffer();
        }
        while (readBuffer.hasRemaining()) {
            byte b = readBuffer.get();
            if (b == '\r') continue;
            if (b == '\n') {
                String headerLine = osBuffer.toString(StandardCharsets.US_ASCII);
                osBuffer = null;
                if (headerLine.isEmpty()) {
                    return State.PROCESS;
                }
                remoteHeadersBuilder.headerLine(headerLine);
                return State.READ_REMOTE_HEADER_LINE;
            }
            osBuffer.write(b);
            if (osBuffer.getLength() > StandardHttpOptions.HEADER_LINE_MAX_LENGTH.defaultValue()) {
                return State.ERROR;
            }
        }
        if (isEos) {
            return State.ERROR;
        }
        resumeState = State.READ_REMOTE_HEADER_LINE;
        return State.READ;
    }

    protected void processRemote() {
        remoteHeaders = remoteHeadersBuilder.build();
        Run.async(() -> handleRemote());
    }

    abstract protected void handleRemote();

    protected State readRemoteBody() {
        if ((readBuffer.hasRemaining() && counter < length) || (isChunked && length == 0) || (!isChunked && counter == length) || (readBuffer.hasRemaining() && readRemoteBodyUntilEos) || isEos) {
            if ((isChunked && length == 0) || (!isChunked && counter == length) || isEos) {
                try {
                    remoteBodyChannel.close();
                } catch (IOException ignored) {
                }
                return State.BREAK;
            }
            ByteBuffer buffer;
            if (readRemoteBodyUntilEos) {
                buffer = readBuffer;
            } else {
                int size = (int) Math.min(length - counter, readBuffer.remaining());
                counter += size;
                buffer = readBuffer.duplicate();
                buffer.limit(buffer.position() + size);
                readBuffer.position(readBuffer.position() + size);
            }
            remoteBodyChannel.write(buffer, null, new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(Integer result, Void attachment) {
                    run(State.READ_REMOTE_BODY);
                }
                @Override
                public void failed(Throwable exc, Void attachment) {
                    run(State.ERROR);
                }
            });
            return State.BREAK;
        }
        resumeState = State.READ_REMOTE_BODY;
        return State.READ;
    }

    protected State closeConnection() {
        try {
            channel.close();
        } catch (IOException ignored) {
        }
        return State.BREAK;
    }

    protected abstract String getLocalLine();

    protected abstract List<HttpHeader> getLocalHeaders();

    protected abstract HttpBody getLocalBody();

    protected void sendLocalLineAndHeaders() {
        boolean hasBody = getLocalBody() != null && getLocalBody().isPresent();
        byte[] localLine = getLocalLine().getBytes(StandardCharsets.US_ASCII);
        List<byte[]> headerLines = new ArrayList<>();
        getLocalHeaders()
                .stream()
                .map(HttpHeader::getHeaderLine)
                .map(String::getBytes)
                .forEach(headerLines::add);
        writeBuffer = ByteBuffer.allocate(localLine.length + headerLines.stream().mapToInt(array -> array.length).sum() + (headerLines.size() * 2) + 4);
        writeBuffer.put(localLine).put(CRLF);
        headerLines.forEach(array -> writeBuffer.put(array).put(CRLF));
        writeBuffer.put(CRLF).flip();
        write(writeBuffer, result -> {
            writeBuffer = null;
            if (hasBody) {
                sendLocalBody();
            } else {
                if (shutdownOutboundAfterBody) {
                    shutdownOutput();
                }
                run(State.READ_REMOTE);
            }
        });
    }

    protected void sendLocalBody() {
        if (writeBuffer == null) {
            writeBuffer = ByteBuffer.allocate(StandardHttpOptions.WRITE_BUFFER_SIZE.defaultValue()).flip();
            localBodyLengthCounter = 0L;
        }
        if (writeBuffer.hasRemaining()) {
            write(writeBuffer, result -> sendLocalBody());
        } else {
            getLocalBody().read(writeBuffer.clear(), null, readSendLocalBody);
        }
    }

    protected void shutdownOutput() {
        try {
            channel.shutdownOutput();
        } catch (IOException ignored) {
        }
    }

    protected void localBodySent() {
        if (shutdownOutboundAfterBody) {
            shutdownOutput();
        }
        run(State.READ_REMOTE);
    }

    protected final CompletionHandler<Integer, Void> readSendLocalBody = new CompletionHandler<>() {
        @Override
        public void completed(Integer result, Void attachment) {
            if (result == -1) {
                if (isLocalBodyChunked) {
                    write(ByteBuffer.wrap(localBodyLengthCounter == 0L ? ZERO_CRLF_CRLF : CRLF_ZERO_CRLF_CRLF), ignored -> localBodySent());
                } else {
                    localBodySent();
                }
            } else {
                writeBuffer.flip();
                if (isLocalBodyChunked) {
                    byte[] hex = Integer.toString(writeBuffer.remaining(), 16).getBytes();
                    ByteBuffer buffer = ByteBuffer.allocate(hex.length + (localBodyLengthCounter == 0L ? 0 : 2) + 2);
                    if (localBodyLengthCounter > 0L) {
                        buffer.put(CRLF);
                    }
                    buffer.put(hex).put(CRLF).flip();
                    localBodyLengthCounter += result;
                    write(buffer, ignored -> sendLocalBody());
                } else {
                    localBodyLengthCounter += result;
                    sendLocalBody();
                }
            }
        }

        @Override
        public void failed(Throwable exc, Void attachment) {
            run(State.ERROR);
        }
    };

    protected void write(ByteBuffer src, Consumer<Integer> callback) {
        channel.write(src, null, new CompletionHandler<Integer, Void>() {
            private int counter;
            @Override
            public void completed(Integer result, Void attachment) {
                counter += result;
                if (src.hasRemaining()) {
                    channel.write(src, attachment, this);
                    return;
                }
                callback.accept(counter);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                run(State.ERROR);
            }
        });
    }

}