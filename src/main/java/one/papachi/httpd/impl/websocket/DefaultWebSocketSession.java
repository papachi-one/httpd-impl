package one.papachi.httpd.impl.websocket;


import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.websocket.WebSocketFrame;
import one.papachi.httpd.api.websocket.WebSocketListener;
import one.papachi.httpd.api.websocket.WebSocketMessage;
import one.papachi.httpd.api.websocket.WebSocketSession;
import one.papachi.httpd.impl.Run;
import one.papachi.httpd.impl.net.GenericCompletionHandler;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

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
    public void setListener(WebSocketListener handler) {
        if (this.handler != null)
            throw new IllegalStateException();
        this.handler = handler;
        Run.async(webSocketConnection);
    }

    @Override
    public void sendClose() {

    }

    @Override
    public CompletableFuture<WebSocketSession> send(ByteBuffer src) {
        return webSocketConnection.send(src);
    }

    @Override
    public CompletableFuture<WebSocketSession> send(AsynchronousByteChannel src) {
        return webSocketConnection.send(src);
    }

    @Override
    public CompletableFuture<WebSocketSession> send(WebSocketFrame src) {
        return webSocketConnection.send(src);
    }

}
