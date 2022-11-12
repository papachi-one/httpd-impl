package one.papachi.httpd.impl.http.client;

import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpHeaders;
import one.papachi.httpd.api.http.HttpOptions;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.api.http.HttpVersion;
import one.papachi.httpd.impl.http.data.DefaultHttpHeader;
import one.papachi.httpd.impl.http.data.DefaultHttpHeaders;
import one.papachi.httpd.impl.http.data.DefaultHttpResponse;
import one.papachi.httpd.impl.http.Http2Connection;
import one.papachi.httpd.impl.http.Http2ConnectionIO;

import java.nio.channels.AsynchronousSocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class Http2ClientConnection extends Http2Connection implements HttpClientConnection {

    protected final Map<Integer, CompletableFuture<HttpResponse>> completableFutures = Collections.synchronizedMap(new HashMap<>());

    public Http2ClientConnection(HttpOptions options, AsynchronousSocketChannel channel) {
        super(options, channel, Http2ConnectionIO.Mode.CLIENT);
        sendMagic();
        sendInitialSettings();
    }

    @Override
    public void close() {
        closeConnection();
    }

    @Override
    public CompletableFuture<HttpResponse> send(HttpRequest request) {
        int streamId = nextStreamId.getAndAdd(2);
        CompletableFuture<HttpResponse> completableFuture = new CompletableFuture<>();
        completableFutures.put(streamId, completableFuture);
        HttpHeaders.Builder builder = new DefaultHttpHeaders.DefaultBuilder();
        builder.header(":method", request.getMethod().name());
        builder.header(":path", request.getPath());
        builder.header(":scheme", "https");
        for (HttpHeader header : request.getHeaders()) {
            if (header.getName().equalsIgnoreCase("Connection") || header.getName().equalsIgnoreCase("Transfer-Encoding") || header.getName().equalsIgnoreCase("Content-Length")) {
            } else if (header.getName().equalsIgnoreCase("Host")) {
                builder.header(":authority", header.getValue());
            } else {
                builder.header(new DefaultHttpHeader.DefaultBuilder().name(header.getName().toLowerCase()).value(header.getValue()).build());
            }
        }
        if (request.getHttpBody() != null && request.getHttpBody().isPresent()) {
            String contentLength = request.getHeaderValue("Content-Length");
            if (contentLength != null) {
                builder.header(new DefaultHttpHeader.DefaultBuilder().name("Content-Length".toLowerCase()).value(contentLength).build());
            }
        }
        List<HttpHeader> headers = builder.build().getHeaders();
        sendLocalHeaders(streamId, headers, request.getHttpBody());
        return completableFuture;
    }

    @Override
    public boolean isIdle() {
        return true;
    }

    @Override
    protected void handleRemote(int streamId) {
        CompletableFuture<HttpResponse> completableFuture = completableFutures.remove(streamId);
        HttpResponse.Builder builder = new DefaultHttpResponse.DefaultBuilder();
        builder.version(HttpVersion.HTTP_2);
        for (HttpHeader header : remoteHeaders.getHeaders()) {
            if (header.getName().equalsIgnoreCase(":status")) {
                builder.statusCode(Integer.parseInt(header.getValue()));
            } else {
                builder.header(header);
            }
        }
        builder.body(remoteBody);
        HttpResponse response = builder.build();
        completableFuture.completeAsync(() -> response);
    }

}
