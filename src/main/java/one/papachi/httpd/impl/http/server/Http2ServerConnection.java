package one.papachi.httpd.impl.http.server;

import one.papachi.httpd.api.http.HttpHandler;
import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpHeaders;
import one.papachi.httpd.api.http.HttpMethod;
import one.papachi.httpd.api.http.HttpOptions;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.api.http.HttpVersion;
import one.papachi.httpd.impl.Run;
import one.papachi.httpd.impl.http.DefaultHttpHeaders;
import one.papachi.httpd.impl.http.DefaultHttpRequest;
import one.papachi.httpd.impl.http.Http2Connection;
import one.papachi.httpd.impl.http.Http2ConnectionIO;

import java.nio.channels.AsynchronousSocketChannel;
import java.util.List;

public class Http2ServerConnection extends Http2Connection {

    protected final HttpHandler handler;

    public Http2ServerConnection(HttpOptions options, AsynchronousSocketChannel channel, HttpHandler handler) {
        super(options, channel, Http2ConnectionIO.Mode.SERVER);
        this.handler = handler;
        sendInitialSettings();
    }

    @Override
    protected void handleRemote(int streamId) {
        HttpRequest.Builder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.version(HttpVersion.HTTP_2);
        for (HttpHeader header : remoteHeaders.getHeaders()) {
            if (header.getName().equalsIgnoreCase(":method"))
                builder.method(HttpMethod.valueOf(header.getValue()));
            else if (header.getName().equalsIgnoreCase(":path"))
                builder.path(header.getValue());
            else if (header.getName().equalsIgnoreCase(":authority"))
                builder.header("Host", header.getValue());
            else
                builder.header(header);
        }
        builder.body(remoteBody);
        HttpRequest request = builder.build();
        Run.async(() -> handler.handle(request).whenComplete((response, throwable) -> onResponse(streamId, response, throwable)));
    }

    private void onResponse(int streamId, HttpResponse response, Throwable throwable) {
        if (throwable != null) {
            throwable.printStackTrace();;
            return;
        }
        HttpHeaders.Builder builder = new DefaultHttpHeaders.DefaultBuilder();
        builder.header(":status", Integer.toString(response.getStatusCode()));
        response.getHeaders().forEach(builder::header);
        List<HttpHeader> headers = builder.build().getHeaders();
        sendLocalHeaders(streamId, headers, response.getHttpBody());
    }

}
