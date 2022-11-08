package one.papachi.httpd.impl.http.client;

import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.impl.CustomDataBuffer;
import one.papachi.httpd.impl.Run;
import one.papachi.httpd.impl.http.DefaultHttpBody;
import one.papachi.httpd.impl.http.DefaultHttpResponse;
import one.papachi.httpd.impl.http.Http1RemoteBodyChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class Http1ClientConnectionDeprecated implements HttpClientConnection, Runnable {

    enum State {
        READ, READ_CHUNK_SIZE, READ_RESPONSE, READ_STATUS_LINE, READ_HEADER_LINE, PROCESS_RESPONSE, READ_BODY, ERROR, BREAK
    }

    private static final byte[] CRLF = new byte[]{'\r', '\n'};

    public final AsynchronousSocketChannel channel;
    public final ByteBuffer readBuffer;
    public volatile State state, resumeState;
    public volatile ByteBuffer writeBuffer;
    protected volatile CustomDataBuffer osBuffer;
    protected volatile boolean isChunked, isEos;
    protected volatile long length, counter;
    public volatile CompletableFuture<HttpResponse> completableFuture;
    protected volatile HttpRequest request;
    protected volatile HttpResponse.Builder responseBuilder;
    protected volatile HttpResponse response;
    protected volatile Http1RemoteBodyChannel bodyChannel;

    public Http1ClientConnectionDeprecated(AsynchronousSocketChannel channel) throws Exception {
        this.channel = channel;
        this.readBuffer = ByteBuffer.allocate(32 * 1024);// TODO get size from HttpOption
    }

    public boolean isIdle() {
        return completableFuture == null;
    }

    @Override
    public CompletableFuture<HttpResponse> send(HttpRequest request) {
        this.request = request;
        completableFuture = new CompletableFuture<>();
        Run.async(() -> sendRequestLineAndHeaders());
        return completableFuture;
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
                    state = isChunked ? State.READ_CHUNK_SIZE : resumeState;
                } else {
                    state = State.BREAK;
                    read();
                    break;
                }
            } else if (state == State.READ_CHUNK_SIZE) {
                state = readChunkSize();
            } else if (state == State.READ_RESPONSE) {
                state = readRequest();
            } else if (state == State.READ_STATUS_LINE) {
                state = readStatusLine();
            } else if (state == State.READ_HEADER_LINE) {
                state = readHeaderLine();
            } else if (state == State.PROCESS_RESPONSE) {
                state = processResponse();
            } else if (state == State.READ_BODY) {
                state = readResponseBody();
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

    protected State readRequest() {
        responseBuilder = new DefaultHttpResponse.DefaultBuilder();
        counter = length = 0;
        isChunked = false;
        return State.READ_STATUS_LINE;
    }

    protected State readStatusLine() {
        if (osBuffer == null) {
            osBuffer = new CustomDataBuffer();
        }
        while (readBuffer.hasRemaining()) {
            byte b = readBuffer.get();
            if (b == '\r') continue;
            if (b == '\n') {
                String statusLine = osBuffer.toString(StandardCharsets.US_ASCII);
                osBuffer = null;
                responseBuilder.setStatusLine(statusLine);
                return State.READ_HEADER_LINE;
            }
            osBuffer.write(b);
            // TODO
//                if (osBuffer.getLength() > server.getOption(StandardHttpOptions.REQUEST_LINE_MAX_LENGTH)) {
//                    return State.ERROR;
//                }
        }
        resumeState = State.READ_STATUS_LINE;
        return State.READ;
    }

    protected State readHeaderLine() {
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
                    return State.PROCESS_RESPONSE;
                }
                responseBuilder.addHeaderLine(headerLine);
                return State.READ_HEADER_LINE;
            }
            osBuffer.write(b);
            // TODO
//                if (osBuffer.getLength() > server.getOption(StandardHttpOptions.HEADER_LINE_MAX_LENGTH)) {
//                    return State.ERROR;
//                }
        }
        resumeState = State.READ_HEADER_LINE;
        return State.READ;
    }

    protected State processResponse() {
        bodyChannel = new Http1RemoteBodyChannel(() -> run(State.READ_BODY));
        HttpBody body = new DefaultHttpBody.DefaultBuilder().setInput(bodyChannel).build();
        responseBuilder.setBody(body);
        response = responseBuilder.build();
        length = Long.parseLong(Optional.ofNullable(response.getHeaderValue("Content-Length")).orElse("0"));
        if ("chunked".equals(response.getHeaderValue("Transfer-Encoding"))) {
            isChunked = true;
            length = -1;
        }
        completableFuture.completeAsync(() -> response);
        // TODO null completable future after body was read
        return State.BREAK;
    }

    protected State readResponseBody() {
        if ((readBuffer.hasRemaining() && counter < length) || (isChunked && length == 0) || (!isChunked && counter == length)) {
            if ((isChunked && length == 0) || (!isChunked && counter == length)) {
                bodyChannel.closeInbound();
            } else if (isChunked) {
                int size = (int) (length - counter);
                size = Math.min(size, readBuffer.remaining());
                counter += size;
                ByteBuffer duplicate = readBuffer.duplicate();
                duplicate.limit(duplicate.position() + size);
                readBuffer.position(readBuffer.position() + size);
                bodyChannel.put(duplicate);
            } else {
                long lSize = (length - counter);
                int size = (int) Math.min(lSize, readBuffer.remaining());
                counter += size;
                ByteBuffer duplicate = readBuffer.duplicate();
                duplicate.limit(duplicate.position() + size);
                readBuffer.position(readBuffer.position() + size);
                bodyChannel.put(duplicate);
            }
            return State.BREAK;
        }
        resumeState = State.READ_BODY;
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

    private void sendRequestLineAndHeaders() {
        boolean hasBody = request.getHttpBody() != null && request.getHttpBody().isPresent();
        byte[] requestLine = request.getRequestLine().getBytes(StandardCharsets.US_ASCII);
        List<byte[]> headerLines = new ArrayList<>();
        request.getHeaders()
                .stream()
                .filter(header -> !header.getName().equalsIgnoreCase("Content-Length"))
                .filter(header -> !header.getName().equalsIgnoreCase("Transfer-Encoding"))
                .map(HttpHeader::getHeaderLine)
                .map(String::getBytes)
                .forEach(headerLines::add);
        headerLines.add(hasBody ? "Transfer-Encoding: chunked".getBytes(StandardCharsets.US_ASCII) : "Content-Length: 0".getBytes(StandardCharsets.US_ASCII));
        writeBuffer = ByteBuffer.allocate(requestLine.length + headerLines.stream().mapToInt(array -> array.length).sum() + (headerLines.size() * 2) + 4);
        writeBuffer.put(requestLine).put(CRLF);
        headerLines.forEach(array -> writeBuffer.put(array).put(CRLF));
        writeBuffer.put(CRLF).flip();
        write(writeBuffer, result -> {
            writeBuffer = null;
            if (hasBody) {
                sendRequestBody();
            } else {
                run(State.READ_RESPONSE);
            }
        });
    }

    private void sendRequestBody() {
        if (writeBuffer == null) {
            writeBuffer = ByteBuffer.allocate(16 * 1024);// TODO get size from HttpOption
            readRequestBody(writeBuffer, result -> {
                if (result == -1) {
                    write(ByteBuffer.wrap(new byte[]{'0', '\r', '\n', '\r', '\n'}), result1 -> run(State.READ_RESPONSE));
                    return;
                }
                writeBuffer.flip();
                write(ByteBuffer.wrap((Integer.toString(writeBuffer.remaining(), 16) + "\r\n").getBytes()), result1 -> sendRequestBody());
            });
            return;
        }
        if (writeBuffer.hasRemaining()) {
            write(writeBuffer, result -> sendRequestBody());
        } else {
            readRequestBody(writeBuffer.clear(), result -> {
                if (result == -1) {
                    write(ByteBuffer.wrap(new byte[]{'\r', '\n', '0', '\r', '\n', '\r', '\n'}), result1 -> run(State.READ_RESPONSE));
                    return;
                }
                writeBuffer.flip();
                write(ByteBuffer.wrap(("\r\n" + Integer.toString(writeBuffer.remaining(), 16) + "\r\n").getBytes()), result1 -> sendRequestBody());
            });
        }
    }

    private void readRequestBody(ByteBuffer dst, Consumer<Integer> callback) {
        request.getHttpBody().read(dst, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                callback.accept(result);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                run(State.ERROR);
            }
        });
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
                // TODO
            }
        });
    }

}