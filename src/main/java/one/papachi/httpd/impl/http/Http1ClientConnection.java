package one.papachi.httpd.impl.http;

import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpHeaders;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.impl.http.client.HttpClientConnection;

import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.CompletableFuture;

public class Http1ClientConnection extends Http1Connection implements HttpClientConnection {

    protected volatile HttpRequest request;

    protected volatile HttpResponse response;

    protected volatile CompletableFuture<HttpResponse> completableFuture;

    protected volatile boolean isIdle = true;

    public Http1ClientConnection(AsynchronousSocketChannel channel) {
        super(channel);
    }

    @Override
    public CompletableFuture<HttpResponse> send(HttpRequest request) {
        isIdle = false;
        this.request = request;
        completableFuture = new CompletableFuture<>();
        sendLocalLineAndHeaders();
        return completableFuture;
    }

    @Override
    public boolean isIdle() {
        return isIdle;
    }

    @Override
    void handleRemote() {
        // response
        HttpResponse.Builder builder = new DefaultHttpResponse.DefaultBuilder();
        builder.setStatusLine(remoteLine);
        builder.setHeaders(remoteHeaders);
        builder.setBody(remoteBody);
        response = builder.build();
        isIdle = true;
        completableFuture.completeAsync(() -> response);
    }

    @Override
    String getLocalLine() {
        return request.getRequestLine();
    }

    @Override
    HttpHeaders getLocalHeaders() {
        return request.getHttpHeaders();
    }

    @Override
    HttpBody getLocalBody() {
        return request.getHttpBody();
    }

}
