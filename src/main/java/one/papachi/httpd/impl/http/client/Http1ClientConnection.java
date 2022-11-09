package one.papachi.httpd.impl.http.client;

import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.api.http.HttpVersion;
import one.papachi.httpd.impl.http.DefaultHttpHeader;
import one.papachi.httpd.impl.http.DefaultHttpResponse;
import one.papachi.httpd.impl.http.Http1Connection;

import java.nio.channels.AsynchronousSocketChannel;
import java.util.ArrayList;
import java.util.List;
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
    protected void handleRemote() {
        HttpResponse.Builder builder = new DefaultHttpResponse.DefaultBuilder();
        String[] split = remoteLine.split(" ", 3);
        builder.setVersion("HTTP/1.0".equals(split[0]) ? HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1);
        builder.setStatusCode(Integer.parseInt(split[1]));
        builder.setReasonPhrase(split.length == 3 ? split[2] : null);
        builder.setHeaders(remoteHeaders);
        builder.setBody(remoteBody);
        response = builder.build();
        isIdle = true;
        completableFuture.completeAsync(() -> response);
    }

    @Override
    protected String getLocalLine() {
        StringBuilder sb = new StringBuilder().append(request.getMethod().name()).append(' ').append(request.getPath()).append(' ');
        sb.append(request.getVersion() == HttpVersion.HTTP_1_0 ? "HTTP/1.0" : "HTTP/1.1");
        return sb.toString();
    }

    @Override
    protected List<HttpHeader> getLocalHeaders() {
        String connection = request.getHeaderValue("Connection");
        String transferEncoding = request.getHeaderValue("Transfer-Encoding");
        String contentLength = request.getHeaderValue("Content-Length");
        List<HttpHeader> list = new ArrayList<>();
        for (HttpHeader header : request.getHeaders()) {
            if (header.getName().equalsIgnoreCase("Connection") || header.getName().equalsIgnoreCase("Transfer-Encoding") || header.getName().equalsIgnoreCase("Content-Length"))
                continue;
            list.add(header);
        }
        if (request.getVersion() != HttpVersion.HTTP_1_0) {
            list.add(new DefaultHttpHeader.DefaultBuilder().setName("Connection").setValue(connection == null ? "keep-alive" : connection).build());
            if (request.getHttpBody() != null && request.getHttpBody().isPresent()) {
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
        return request.getHttpBody();
    }

}
