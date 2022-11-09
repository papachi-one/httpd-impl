package one.papachi.httpd.impl.http.server;

import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpHandler;
import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpMethod;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.api.http.HttpVersion;
import one.papachi.httpd.impl.http.DefaultHttpHeader;
import one.papachi.httpd.impl.http.DefaultHttpRequest;
import one.papachi.httpd.impl.http.Http1Connection;

import java.nio.channels.AsynchronousSocketChannel;
import java.util.ArrayList;
import java.util.List;

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
        String connection = response.getHeaderValue("Connection");
        String transferEncoding = response.getHeaderValue("Transfer-Encoding");
        String contentLength = response.getHeaderValue("Content-Length");
        List<HttpHeader> list = new ArrayList<>();
        for (HttpHeader header : response.getHeaders()) {
            if (header.getName().equalsIgnoreCase("Connection") || header.getName().equalsIgnoreCase("Transfer-Encoding") || header.getName().equalsIgnoreCase("Content-Length"))
                continue;
            list.add(header);
        }
        if (request.getVersion() != HttpVersion.HTTP_1_0) {
            list.add(new DefaultHttpHeader.DefaultBuilder().setName("Connection").setValue(connection == null ? "keep-alive" : connection).build());
            if (response.getHttpBody() != null && response.getHttpBody().isPresent()) {
                if (contentLength == null || transferEncoding.equals("chunked")) {
                    list.add(new DefaultHttpHeader.DefaultBuilder().setName("Transfer-Encoding").setValue("chunked").build());
                } else {
                    list.add(new DefaultHttpHeader.DefaultBuilder().setName("Content-Length").setValue(contentLength).build());
                }
            } else {
                list.add(new DefaultHttpHeader.DefaultBuilder().setName("Content-Length").setValue("0").build());
            }
        }
        return list;
    }

    @Override
    protected HttpBody getLocalBody() {
        return response.getHttpBody();
    }

}
