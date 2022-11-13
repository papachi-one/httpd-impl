package one.papachi.httpd.impl.spi;

import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpHeaders;
import one.papachi.httpd.api.spi.WebSocketDataProvider;
import one.papachi.httpd.api.websocket.WebSocketRequest;
import one.papachi.httpd.impl.http.data.DefaultHttpHeader;
import one.papachi.httpd.impl.http.data.DefaultHttpHeaders;
import one.papachi.httpd.impl.websocket.DefaultWebSocketRequest;

public class DefaultWebSocketDataProvider implements WebSocketDataProvider {

    @Override
    public WebSocketRequest.Builder getWebSocketRequestBuilder() {
        return new DefaultWebSocketRequest.DefaultBuilder();
    }

    @Override
    public HttpHeaders.Builder getHttpHeadersBuilder() {
        return new DefaultHttpHeaders.DefaultBuilder();
    }

    @Override
    public HttpHeader.Builder getHttpHeaderBuilder() {
        return new DefaultHttpHeader.DefaultBuilder();
    }

}
