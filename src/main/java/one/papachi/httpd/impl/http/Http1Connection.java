package one.papachi.httpd.impl.http;


import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpHeaders;
import one.papachi.httpd.impl.CustomDataBuffer;
import one.papachi.httpd.impl.StandardHttpOptions;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public abstract class Http1Connection implements Runnable {

    enum State {
        READ, READ_CHUNK_SIZE, READ_REMOTE, READ_REMOTE_LINE, READ_REMOTE_HEADER_LINE, READ_REMOTE_BODY, PROCESS, BREAK, ERROR,
    }

    private static final byte[] CRLF = new byte[]{'\r', '\n'};

    private static final byte[] ZERO_CRLF_CRLF = new byte[]{'0', '\r', '\n', '\r', '\n'};

    private static final byte[] CRLF_ZERO_CRLF_CRLF = new byte[]{'\r', '\n', '0', '\r', '\n', '\r', '\n'};

    private final AsynchronousSocketChannel channel;
    private final ByteBuffer readBuffer;
    private volatile ByteBuffer writeBuffer;
    protected volatile State state = State.READ_REMOTE, resumeState;
    protected volatile CustomDataBuffer osBuffer;
    protected volatile boolean isChunked;
    protected volatile boolean isEos;
    protected volatile long length, counter;
    protected volatile String remoteLine;
    protected volatile HttpHeaders.Builder remoteHeadersBuilder;
    protected volatile HttpHeaders remoteHeaders;
    protected volatile Http1RemoteBodyChannel remoteBodyChannel;
    protected volatile HttpBody remoteBody;

    public Http1Connection(AsynchronousSocketChannel channel) {
        this.channel = channel;
        this.readBuffer = ByteBuffer.allocate(StandardHttpOptions.READ_BUFFER_SIZE.defaultValue()).flip();
    }

    protected void run(State state) {
        this.state = state;
        run();
    }

    @Override
    public void run() {
        while (true) {
            if (state == State.READ) {
                if (readBuffer.hasRemaining()) {
                    state = isChunked ? State.READ_CHUNK_SIZE : resumeState;
                } else {
                    state = State.BREAK;
                    read();
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
                state = processRemote();
            } else if (state == State.READ_REMOTE_BODY) {
                state = readRemoteBody();
            } else if (state == State.ERROR) {
                state = close();
            }
            if (state == State.BREAK)
                break;
        }
    }

    protected State read() {
        channel.read(readBuffer.compact(), null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                if (result == -1) {
                    isEos = true;
                    run(resumeState);
                    return;
                }
                readBuffer.flip();
                run(isChunked ? State.READ_CHUNK_SIZE : resumeState);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                run(State.ERROR);
            }
        });
        return State.BREAK;
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
                remoteHeadersBuilder.addHeaderLine(headerLine);
                return State.READ_REMOTE_HEADER_LINE;
            }
            osBuffer.write(b);
            if (osBuffer.getLength() > StandardHttpOptions.HEADER_LINE_MAX_LENGTH.defaultValue()) {
                return State.ERROR;
            }
        }
        resumeState = State.READ_REMOTE_HEADER_LINE;
        return State.READ;
    }

    protected State processRemote() {
        remoteHeaders = remoteHeadersBuilder.build();
        length = Long.parseLong(Optional.ofNullable(remoteHeaders.getHeaderValue("Content-Length")).orElse("0"));
        if ("chunked".equals(remoteHeaders.getHeaderValue("Transfer-Encoding"))) {
            isChunked = true;
            length = -1;
        }
        if (isChunked || length > 0) {
            remoteBodyChannel = new Http1RemoteBodyChannel(() -> run(State.READ_REMOTE_BODY));
            remoteBody = new DefaultHttpBody.DefaultBuilder().setInput(remoteBodyChannel).build();
        } else {
            remoteBody = new DefaultHttpBody.DefaultBuilder().setEmpty().build();
        }
        handleRemote();
        return State.BREAK;
    }

    abstract void handleRemote();

    protected State readRemoteBody() {
        if ((readBuffer.hasRemaining() && counter < length) || (isChunked && length == 0) || (!isChunked && counter == length)) {
            if ((isChunked && length == 0) || (!isChunked && counter == length)) {
                remoteBodyChannel.closeInbound();
            } else if (isChunked) {
                int size = (int) (length - counter);
                size = Math.min(size, readBuffer.remaining());
                counter += size;
                ByteBuffer duplicate = readBuffer.duplicate();
                duplicate.limit(duplicate.position() + size);
                readBuffer.position(readBuffer.position() + size);
                remoteBodyChannel.put(duplicate);
            } else {
                long lSize = (length - counter);
                int size = (int) Math.min(lSize, readBuffer.remaining());
                counter += size;
                ByteBuffer duplicate = readBuffer.duplicate();
                duplicate.limit(duplicate.position() + size);
                readBuffer.position(readBuffer.position() + size);
                remoteBodyChannel.put(duplicate);
            }
            return State.BREAK;
        }
        resumeState = State.READ_REMOTE_BODY;
        return State.READ;
    }

    protected State close() {
        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return State.BREAK;
    }

    abstract String getLocalLine();

    abstract HttpHeaders getLocalHeaders();

    abstract HttpBody getLocalBody();

    protected void sendLocalLineAndHeaders() {
        boolean hasBody = getLocalBody() != null && getLocalBody().isPresent();
        byte[] statusLine = getLocalLine().getBytes(StandardCharsets.US_ASCII);
        List<byte[]> headerLines = new ArrayList<>();
        getLocalHeaders().getHeaders()
                .stream()
                .filter(header -> !header.getName().equalsIgnoreCase("Content-Length"))
                .filter(header -> !header.getName().equalsIgnoreCase("Transfer-Encoding"))
                .map(HttpHeader::getHeaderLine)
                .map(String::getBytes)
                .forEach(headerLines::add);
        headerLines.add(hasBody ? "Transfer-Encoding: chunked".getBytes(StandardCharsets.US_ASCII) : "Content-Length: 0".getBytes(StandardCharsets.US_ASCII));
        writeBuffer = ByteBuffer.allocate(statusLine.length + headerLines.stream().mapToInt(array -> array.length).sum() + (headerLines.size() * 2) + 4);
        writeBuffer.put(statusLine).put(CRLF);
        headerLines.forEach(array -> writeBuffer.put(array).put(CRLF));
        writeBuffer.put(CRLF).flip();
        write(writeBuffer, result -> {
            writeBuffer = null;
            if (hasBody) {
                sendLocalBody();
            } else {
                run(State.READ_REMOTE);
            }
        });
    }

    protected void sendLocalBody() {
        if (writeBuffer == null) {
            writeBuffer = ByteBuffer.allocate(StandardHttpOptions.WRITE_BUFFER_SIZE.defaultValue());
            getLocalBody().read(writeBuffer.clear(), null, new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(Integer result, Void attachment) {
                    if (result == -1) {
                        write(ByteBuffer.wrap(ZERO_CRLF_CRLF), result1 -> run(State.READ_REMOTE));
                    } else {
                        writeBuffer.flip();
                        write(ByteBuffer.wrap((Integer.toString(writeBuffer.remaining(), 16) + "\r\n").getBytes()), result1 -> sendLocalBody());
                    }
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    run(State.ERROR);
                }
            });
        } else if (writeBuffer.hasRemaining()) {
            write(writeBuffer, result -> sendLocalBody());
        } else {
            getLocalBody().read(writeBuffer.clear(), null, new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(Integer result, Void attachment) {
                    if (result == -1) {
                        write(ByteBuffer.wrap(CRLF_ZERO_CRLF_CRLF), result1 -> run(State.READ_REMOTE));
                    } else {
                        writeBuffer.flip();
                        write(ByteBuffer.wrap(("\r\n" + Integer.toString(writeBuffer.remaining(), 16) + "\r\n").getBytes()), result1 -> sendLocalBody());
                    }
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    run(State.ERROR);
                }
            });
        }
    }

    private void write(ByteBuffer src, Consumer<Integer> callback) {
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

    private static String getSecWebSocketAccept(String secWebSocketKey) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((secWebSocketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

}