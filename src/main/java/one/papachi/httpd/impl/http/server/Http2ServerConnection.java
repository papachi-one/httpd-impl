package one.papachi.httpd.impl.http.server;

import one.papachi.httpd.api.http.HttpHandler;
import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpHeaders;
import one.papachi.httpd.api.http.HttpMethod;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.api.http.HttpVersion;
import one.papachi.httpd.impl.http.DefaultHttpHeaders;
import one.papachi.httpd.impl.http.DefaultHttpRequest;
import one.papachi.httpd.impl.http.Http2Connection;
import one.papachi.httpd.impl.http.Http2ConnectionIO;

import java.nio.channels.AsynchronousSocketChannel;
import java.util.List;

public class Http2ServerConnection extends Http2Connection {

    protected final HttpHandler handler;

    public Http2ServerConnection(AsynchronousSocketChannel channel, HttpHandler handler) {
        super(channel, Http2ConnectionIO.Mode.SERVER);
        this.handler = handler;
        sendInitialSettings();
    }

    @Override
    protected void handleRemote(int streamId) {
        HttpRequest.Builder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.setVersion(HttpVersion.HTTP_2);
        for (HttpHeader header : remoteHeaders.getHeaders()) {
            if (header.getName().equalsIgnoreCase(":method"))
                builder.setMethod(HttpMethod.valueOf(header.getValue()));
            else if (header.getName().equalsIgnoreCase(":path"))
                builder.setPath(header.getValue());
            else if (header.getName().equalsIgnoreCase(":authority"))
                builder.addHeader("Host", header.getValue());
            else
                builder.addHeader(header);
        }
        builder.setBody(remoteBody);
        HttpRequest request = builder.build();
        handler.handle(request).whenComplete((response, throwable) -> onResponse(streamId, response, throwable));
    }

    private void onResponse(int streamId, HttpResponse response, Throwable throwable) {
        if (throwable != null) {
            throwable.printStackTrace();;
            return;
        }
        HttpHeaders.Builder builder = new DefaultHttpHeaders.DefaultBuilder();
        builder.addHeader(":status", Integer.toString(response.getStatusCode()));
        response.getHeaders().forEach(builder::addHeader);
        List<HttpHeader> headers = builder.build().getHeaders();
        sendLocalHeaders(streamId, headers, response.getHttpBody());
    }

}
