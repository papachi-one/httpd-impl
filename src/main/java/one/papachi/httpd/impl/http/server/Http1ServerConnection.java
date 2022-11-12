package one.papachi.httpd.impl.http.server;

import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpHandler;
import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpMethod;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.api.http.HttpVersion;
import one.papachi.httpd.impl.http.DefaultHttpBody;
import one.papachi.httpd.impl.http.DefaultHttpHeader;
import one.papachi.httpd.impl.http.DefaultHttpRequest;
import one.papachi.httpd.impl.http.Http1Connection;
import one.papachi.httpd.impl.http.Http1RemoteBodyChannel;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Http1ServerConnection extends Http1Connection {

    protected volatile HttpRequest request;

    protected volatile HttpResponse response;

    protected final HttpHandler handler;

    public Http1ServerConnection(AsynchronousSocketChannel channel, HttpHandler handler) {
        super(channel);
        this.handler = handler;
        run(State.READ_REMOTE);
    }

    @Override
    protected void handleRemote() {
        HttpRequest.Builder builder = new DefaultHttpRequest.DefaultBuilder();
        String[] split = remoteLine.split(" ", 3);
        builder.setMethod(HttpMethod.valueOf(split[0]));
        builder.setPath(split[1]);
        switch (split.length == 3 ? split[2] : "") {
            case "HTTP/1.0" -> builder.setVersion(HttpVersion.HTTP_1_0);
            default -> builder.setVersion(HttpVersion.HTTP_1_1);
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
            write(buffer, ignored -> close());
            return;
        } else if (contentLength != null && contentLength >= 0) {
            length = contentLength;
        } else {
            hasRemoteBody = false;
        }
        remoteBody = new DefaultHttpBody.DefaultBuilder().setInput(hasRemoteBody ? (remoteBodyChannel = new Http1RemoteBodyChannel(() -> run(State.READ_REMOTE_BODY))) : null).build();

        builder.setHeaders(remoteHeaders);
        builder.setBody(remoteBody);
        request = builder.build();

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
            list.add(new DefaultHttpHeader.DefaultBuilder().setName("Connection").setValue("close".equals(request.getHeaderValue("Connection")) || "close".equals(connection) ? "close" : "keep-alive").build());
            if (response.getHttpBody() != null && response.getHttpBody().isPresent()) {
                if ((contentLength == null || "chunked".equals(transferEncoding)) && !shutdownOutboundAfterBody) {
                    isLocalBodyChunked = true;
                    list.add(new DefaultHttpHeader.DefaultBuilder().setName("Transfer-Encoding").setValue("chunked").build());
                } else if (contentLength != null) {
                    list.add(new DefaultHttpHeader.DefaultBuilder().setName("Content-Length").setValue(contentLength).build());
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

}
