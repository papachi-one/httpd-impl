package one.papachi.httpd.impl.spi;

import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpHeaders;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.api.http.HttpServer;
import one.papachi.httpd.api.spi.HttpServerProvider;
import one.papachi.httpd.impl.http.server.DefaultHttpServer;

import java.io.IOException;
import java.nio.channels.AsynchronousServerSocketChannel;

public class DefaultHttpServerProvider implements HttpServerProvider {

    @Override
    public HttpServer getHttpServerInstance() {
        try {
            return new DefaultHttpServer(AsynchronousServerSocketChannel.open());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public HttpRequest.Builder getHttpRequestBuilder() {
        return null;
    }

    @Override
    public HttpResponse.Builder getHttpResponseBuilder() {
        return null;
    }

    @Override
    public HttpHeaders.Builder getHttpHeadersBuilder() {
        return null;
    }

    @Override
    public HttpHeader.Builder getHttpHeaderBuilder() {
        return null;
    }

    @Override
    public HttpBody.Builder getHttpBodyBuilder() {
        return null;
    }

}
