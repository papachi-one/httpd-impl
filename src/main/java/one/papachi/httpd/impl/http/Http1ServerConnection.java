package one.papachi.httpd.impl.http;

import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpHandler;
import one.papachi.httpd.api.http.HttpHeaders;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;

import java.nio.channels.AsynchronousSocketChannel;

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
    void handleRemote() {
        // request
        HttpRequest.Builder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.setRequestLine(remoteLine);
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
    String getLocalLine() {
        return response.getStatusLine();
    }

    @Override
    HttpHeaders getLocalHeaders() {
        return response.getHttpHeaders();
    }

    @Override
    HttpBody getLocalBody() {
        return response.getHttpBody();
    }

}
