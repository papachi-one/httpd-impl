package one.papachi.httpd.impl.http.server;


import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpConnection;
import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.api.http.HttpServer;
import one.papachi.httpd.impl.CustomDataBuffer;
import one.papachi.httpd.impl.StandardHttpOptions;
import one.papachi.httpd.impl.http.DefaultHttpBody;
import one.papachi.httpd.impl.http.DefaultHttpHeader;
import one.papachi.httpd.impl.http.DefaultHttpRequest;
import one.papachi.httpd.impl.http.HttpRequestBodyChannel;
import one.papachi.httpd.impl.net.AsynchronousBufferedSocketChannel;
import one.papachi.httpd.impl.websocket.DefaultWebSocketConnection;

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

public class Http1ServerConnection implements HttpConnection, Runnable {

    enum State {
        READ, READ_CHUNK_SIZE, READ_REQUEST, READ_REQUEST_LINE, READ_HEADER_LINE, READ_BODY, PROCESS_REQUEST, BREAK, ERROR,
    }

    private static final byte[] CRLF = new byte[]{'\r', '\n'};

    private final HttpServer server;
    private final AsynchronousSocketChannel channel;
    private final ByteBuffer readBuffer;
    private ByteBuffer writeBuffer;
    protected State state = State.READ_REQUEST, resumeState;
    protected CustomDataBuffer osBuffer;
    protected boolean isChunked;
    protected boolean isEos;
    protected long length, counter;
    protected HttpRequest.Builder requestBuilder;
    protected HttpRequest request;
    protected HttpRequestBodyChannel bodyChannel;
    protected HttpResponse response;

    public Http1ServerConnection(HttpServer server, AsynchronousSocketChannel channel) {
        this.server = server;
        this.channel = channel;
        this.readBuffer = ByteBuffer.allocate(server.getOption(StandardHttpOptions.READ_BUFFER_SIZE)).flip();
    }

    @Override
    public HttpServer getHttpServer() {
        return server;
    }

    @Override
    public AsynchronousSocketChannel getSocketChannel() {
        return new AsynchronousBufferedSocketChannel(channel, readBuffer);
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
            } else if (state == State.READ_REQUEST) {
                state = readRequest();
            } else if (state == State.READ_REQUEST_LINE) {
                state = readRequestLine();
            } else if (state == State.READ_HEADER_LINE) {
                state = readHeaderLine();
            } else if (state == State.PROCESS_REQUEST) {
                state = processRequest();
            } else if (state == State.READ_BODY) {
                state = readBody();
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
        writeBuffer = null;
        osBuffer = null;
        isChunked = false;
        isEos = false;
        length = counter = 0L;
        requestBuilder = new DefaultHttpRequest.DefaultBuilder();
        request = null;
        bodyChannel = null;
        response = null;
        return State.READ_REQUEST_LINE;
    }

    protected State readRequestLine() {
        if (osBuffer == null) {
            osBuffer = new CustomDataBuffer();
        }
        while (readBuffer.hasRemaining()) {
            byte b = readBuffer.get();
            if (b == '\r') continue;
            if (b == '\n') {
                String requestLine = osBuffer.toString(StandardCharsets.US_ASCII);
                osBuffer = null;
                requestBuilder.setRequestLine(requestLine);
                return State.READ_HEADER_LINE;
            }
            osBuffer.write(b);
            if (osBuffer.getLength() > server.getOption(StandardHttpOptions.REQUEST_LINE_MAX_LENGTH)) {
                return State.ERROR;
            }
        }
        resumeState = State.READ_REQUEST_LINE;
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
                    return State.PROCESS_REQUEST;
                }
                requestBuilder.addHeaderLine(headerLine);
                return State.READ_HEADER_LINE;
            }
            osBuffer.write(b);
            if (osBuffer.getLength() > server.getOption(StandardHttpOptions.HEADER_LINE_MAX_LENGTH)) {
                return State.ERROR;
            }
        }
        resumeState = State.READ_HEADER_LINE;
        return State.READ;
    }

    protected State processRequest() {
        bodyChannel = new HttpRequestBodyChannel(() -> run(State.READ_BODY));
        HttpBody body = new DefaultHttpBody.DefaultBuilder().setInput(bodyChannel).build();
        requestBuilder.setBody(body);
        request = requestBuilder.build();

        {
            String upgrade = request.getHeaderValue("Upgrade");
            String connection = request.getHeaderValue("Connection");
            String secWebSocketKey = request.getHeaderValue("Sec-WebSocket-Key");
            String secWebSocketVersion = request.getHeaderValue("Sec-WebSocket-Version");
            if (upgrade != null && connection != null && secWebSocketKey != null & secWebSocketKey != null && upgrade.equalsIgnoreCase("websocket") && connection.equalsIgnoreCase("upgrade") && secWebSocketVersion.equals("13") && secWebSocketKey != null && !secWebSocketKey.isEmpty()) {
                byte[] response = ("HTTP/1.1 101 Switching Protocols\r\nConnection: upgrade\r\nUpgrade: websocket\r\nSec-WebSocket-Accept: " + getSecWebSocketAccept(secWebSocketKey) + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
                write(ByteBuffer.wrap(response), ignore -> new DefaultWebSocketConnection(DefaultWebSocketConnection.Mode.SERVER, server, channel, readBuffer));
                return State.BREAK;
            }
        }

        length = Long.parseLong(Optional.ofNullable(request.getHeaderValue("Content-Length")).orElse("0"));
        if ("chunked".equals(request.getHeaderValue("Transfer-Encoding"))) {
            isChunked = true;
            length = -1;
        }
        server.getHttpHandler().handle(request).whenCompleteAsync(this::handleResponse);
        return State.BREAK;
    }

    protected State readBody() {
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

    protected void handleResponse(HttpResponse response, Throwable t) {
        this.response = response;
        sendStatusLine();
    }

    protected void sendStatusLine() {
        byte[] statusLine = response.getStatusLine().getBytes(StandardCharsets.US_ASCII);
        writeBuffer = ByteBuffer.allocate(statusLine.length + CRLF.length);
        writeBuffer.put(statusLine);
        writeBuffer.put(CRLF);
        writeBuffer.flip();
        write(writeBuffer, result -> {
            writeBuffer = null;
            sendHeaders();
        });
    }

    protected void sendHeaders() {
        List<byte[]> bytes = new ArrayList<>();
        response.getHeaders().stream().filter(header -> !header.getName().equalsIgnoreCase("Content-Length")).filter(header -> !header.getName().equalsIgnoreCase("Transfer-Encoding")).map(HttpHeader::getHeaderLine).map(headerLine -> headerLine.getBytes(StandardCharsets.US_ASCII)).forEach(bytes::add);
        if (response.getHttpBody().isPresent()) {
            bytes.add(new DefaultHttpHeader.DefaultBuilder().setName("Transfer-Encoding").setValue("chunked").build().getHeaderLine().getBytes(StandardCharsets.US_ASCII));
        } else {
            bytes.add(new DefaultHttpHeader.DefaultBuilder().setName("Content-Length").setValue("0").build().getHeaderLine().getBytes(StandardCharsets.US_ASCII));
        }
        ByteBuffer buffer = ByteBuffer.allocate(bytes.stream().mapToInt(array -> array.length).sum() + (bytes.size() * 2) + 2);
        bytes.forEach(array -> buffer.put(array).put(CRLF));
        buffer.put(CRLF);
        buffer.flip();
        write(buffer, result -> {
            if (response.getHttpBody().isPresent()) {
                sendBody();
            } else {
                run(State.READ_REQUEST);
            }
        });
    }

    protected void sendBody() {
        if (writeBuffer == null) {
            writeBuffer = ByteBuffer.allocate(server.getOption(StandardHttpOptions.WRITE_BUFFER_SIZE));
            read(writeBuffer, result -> {
                if (result == -1) {
                    write(ByteBuffer.wrap(new byte[]{'0', '\r', '\n', '\r', '\n'}), result1 -> run(State.READ_REQUEST));
                    return;
                }
                writeBuffer.flip();
                write(ByteBuffer.wrap((Integer.toString(writeBuffer.remaining(), 16) + "\r\n").getBytes()), result1 -> sendBody());
            });
            return;
        }
        if (writeBuffer.hasRemaining()) {
            write(writeBuffer, result -> sendBody());
        } else {
            read(writeBuffer.clear(), result -> {
                if (result == -1) {
                    write(ByteBuffer.wrap(new byte[]{'\r', '\n', '0', '\r', '\n', '\r', '\n'}), result1 -> run(State.READ_REQUEST));
                    return;
                }
                writeBuffer.flip();
                write(ByteBuffer.wrap(("\r\n" + Integer.toString(writeBuffer.remaining(), 16) + "\r\n").getBytes()), result1 -> sendBody());
            });
        }
    }

    private void read(ByteBuffer dst, Consumer<Integer> callback) {
        response.getHttpBody().read(dst, null, new CompletionHandler<Integer, Void>() {
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
