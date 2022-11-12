package one.papachi.httpd.impl.http.server;

import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpHandler;
import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpMethod;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.api.http.HttpVersion;
import one.papachi.httpd.api.websocket.WebSocketHandler;
import one.papachi.httpd.impl.http.Http1Connection;
import one.papachi.httpd.impl.http.Http1RemoteBodyChannel;
import one.papachi.httpd.impl.http.data.DefaultHttpBody;
import one.papachi.httpd.impl.http.data.DefaultHttpHeader;
import one.papachi.httpd.impl.http.data.DefaultHttpRequest;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

public class Http1ServerConnection extends Http1Connection {

    protected volatile HttpRequest request;

    protected volatile HttpResponse response;

    protected final HttpHandler handler;

    protected final WebSocketHandler webSockethandler;

    public Http1ServerConnection(AsynchronousSocketChannel channel, HttpHandler handler, WebSocketHandler webSocketHandler) {
        super(channel);
        this.handler = handler;
        this.webSockethandler = webSocketHandler;
        run(State.READ_REMOTE);
    }

    @Override
    protected void handleRemote() {
        HttpRequest.Builder builder = new DefaultHttpRequest.DefaultBuilder();
        String[] split = remoteLine.split(" ", 3);
        builder.method(HttpMethod.valueOf(split[0]));
        builder.path(split[1]);
        switch (split.length == 3 ? split[2] : "") {
            case "HTTP/1.0" -> builder.version(HttpVersion.HTTP_1_0);
            default -> builder.version(HttpVersion.HTTP_1_1);
        }

        String transferEncoding = remoteHeaders.getHeaderValue("Transfer-Encoding");
        Long contentLength = Optional.ofNullable(remoteHeaders.getHeaderValue("Content-Length")).map(contentLengthString -> {
            try {
                return Long.parseLong(contentLengthString);
            } catch (Exception ignored) {
                return null;
            }
        }).orElse(null);
        boolean hasRemoteBody = true;
        isChunked = false;
        length = -1;
        if (transferEncoding != null) {
            isChunked = true;
        } else if (contentLength != null && contentLength < 0) {
            isError = true;
            ByteBuffer buffer;
            if (request.getVersion() == HttpVersion.HTTP_1_1) {
                buffer = ByteBuffer.wrap("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes());
            } else {
                buffer = ByteBuffer.wrap("HTTP/1.0 400 Bad Request\r\n\r\n".getBytes());
            }
            write(buffer, ignored -> closeConnection());
            return;
        } else if (contentLength != null && contentLength >= 0) {
            length = contentLength;
        } else {
            hasRemoteBody = false;
        }
        remoteBody = new DefaultHttpBody.DefaultBuilder().input(hasRemoteBody ? (remoteBodyChannel = new Http1RemoteBodyChannel(() -> run(State.READ_REMOTE_BODY))) : null).build();

        builder.headers(remoteHeaders);
        builder.body(remoteBody);
        request = builder.build();


//        if ("websocket".equalsIgnoreCase(request.getHeaderValue("Upgrade"))
//                && "upgrade".equalsIgnoreCase(request.getHeaderValue("Connection"))
//                && request.getHeaderValue("Sec-WebSocket-Key") != null
//                && Optional.ofNullable(request.getHeaderValue("Sec-WebSocket-Version")).orElse("").contains("13")) {
//            byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n" +
//                    "Upgrade: websocket\r\n" +
//                    "Connection: upgrade\r\n" +
//                    "Sec-WebSocket-Accept: " + getSecWebSocketAccept(request.getHeaderValue("Sec-WebSocket-Key").trim()) + "\r\n" +
//                    "\r\n").getBytes();
//            write(ByteBuffer.wrap(response), ignored -> {
//                new DefaultWebSocketConnection(DefaultWebSocketConnection.Mode.SERVER, channel, readBuffer, request, webSockethandler);
//            });
//            return;
//        }


        handler.handle(request).whenComplete(this::onResponse);
    }

    private void onResponse(HttpResponse response, Throwable throwable) {
        if (throwable != null) {
            throwable.printStackTrace();;
            return;
        }
        this.response = response;
        sendLocalLineAndHeaders();
    }

    @Override
    protected String getLocalLine() {
        return new StringBuilder().append(request.getVersion() == HttpVersion.HTTP_1_0 ? "HTTP/1.0" : "HTTP/1.1").append(' ').append(response.getStatusCode()).append(' ').append(response.getReasonPhrase()).toString();
    }

    @Override
    protected List<HttpHeader> getLocalHeaders() {
        isLocalBodyChunked = false;
        localBodyLengthCounter = 0;
        String connection = response.getHeaderValue("Connection");
        String transferEncoding = response.getHeaderValue("Transfer-Encoding");
        String contentLength = response.getHeaderValue("Content-Length");
        List<HttpHeader> list = new ArrayList<>();
        for (HttpHeader header : response.getHeaders()) {
            if (header.getName().equalsIgnoreCase("Connection") || header.getName().equalsIgnoreCase("Transfer-Encoding") || header.getName().equalsIgnoreCase("Content-Length"))
                continue;
            list.add(header);
        }
        if (request.getVersion() == HttpVersion.HTTP_1_0 || "close".equals(request.getHeaderValue("Connection")) || "close".equals(connection))
            shutdownOutboundAfterBody = true;
        if (request.getVersion() != HttpVersion.HTTP_1_0) {
            list.add(new DefaultHttpHeader.DefaultBuilder().name("Connection").value("close".equals(request.getHeaderValue("Connection")) || "close".equals(connection) ? "close" : "keep-alive").build());
            if (response.getHttpBody() != null && response.getHttpBody().isPresent()) {
                if ((contentLength == null || "chunked".equals(transferEncoding)) && !shutdownOutboundAfterBody) {
                    isLocalBodyChunked = true;
                    list.add(new DefaultHttpHeader.DefaultBuilder().name("Transfer-Encoding").value("chunked").build());
                } else if (contentLength != null) {
                    list.add(new DefaultHttpHeader.DefaultBuilder().name("Content-Length").value(contentLength).build());
                }
//            } else {
//                list.add(new DefaultHttpHeader.DefaultBuilder().setName("Content-Length").setValue("0").build());
            }
        }
        return list;
    }

    @Override
    protected HttpBody getLocalBody() {
        return response.getHttpBody();
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
