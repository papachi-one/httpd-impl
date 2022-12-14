package one.papachi.httpd.impl.spi;

import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpHeaders;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.api.spi.HttpDataProvider;
import one.papachi.httpd.impl.http.data.DefaultHttpBody;
import one.papachi.httpd.impl.http.data.DefaultHttpHeader;
import one.papachi.httpd.impl.http.data.DefaultHttpHeaders;
import one.papachi.httpd.impl.http.data.DefaultHttpRequest;
import one.papachi.httpd.impl.http.data.DefaultHttpResponse;

public class DefaultHttpDataProvider implements HttpDataProvider {

    @Override
    public HttpRequest.Builder getHttpRequestBuilder() {
        return new DefaultHttpRequest.DefaultBuilder();
    }

    @Override
    public HttpResponse.Builder getHttpResponseBuilder() {
        return new DefaultHttpResponse.DefaultBuilder();
    }

    @Override
    public HttpHeaders.Builder getHttpHeadersBuilder() {
        return new DefaultHttpHeaders.DefaultBuilder();
    }

    @Override
    public HttpHeader.Builder getHttpHeaderBuilder() {
        return new DefaultHttpHeader.DefaultBuilder();
    }

    @Override
    public HttpBody.Builder getHttpBodyBuilder() {
        return new DefaultHttpBody.DefaultBuilder();
    }

}
