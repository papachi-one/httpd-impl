package one.papachi.httpd.impl.websocket;


import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.websocket.WebSocketFrame;
import one.papachi.httpd.api.websocket.WebSocketListener;
import one.papachi.httpd.api.websocket.WebSocketMessage;
import one.papachi.httpd.api.websocket.WebSocketSession;
import one.papachi.httpd.impl.Run;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.util.concurrent.CompletableFuture;

public class DefaultWebSocketSession implements WebSocketSession {

    private final HttpRequest request;
    private final DefaultWebSocketConnection webSocketConnection;
    private WebSocketListener handler;

    public DefaultWebSocketSession(HttpRequest request, DefaultWebSocketConnection webSocketConnection) {
        this.request = request;
        this.webSocketConnection = webSocketConnection;
    }

    @Override
    public HttpRequest getRequest() {
        return request;
    }

    @Override
    public WebSocketListener getListener() {
        return handler;
    }

    @Override
    public WebSocketSession setListener(WebSocketListener handler) {
        if (this.handler != null)
            throw new IllegalStateException();
        this.handler = handler;
        Run.async(webSocketConnection);
        return this;
    }

    @Override
    public CompletableFuture<WebSocketSession> sendClose() {
        return null;
    }

    @Override
    public CompletableFuture<WebSocketSession> send(ByteBuffer src) {
        return webSocketConnection.send(src);
    }

    @Override
    public CompletableFuture<WebSocketSession> send(WebSocketMessage src) {
        return webSocketConnection.send(src);
    }

    @Override
    public CompletableFuture<WebSocketSession> send(WebSocketFrame src) {
        return webSocketConnection.send(src);
    }

}
