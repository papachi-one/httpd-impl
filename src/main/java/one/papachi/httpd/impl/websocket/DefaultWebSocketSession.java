package one.papachi.httpd.impl.websocket;


import one.papachi.httpd.api.websocket.WebSocketConnection;
import one.papachi.httpd.api.websocket.WebSocketDataHandler;
import one.papachi.httpd.api.websocket.WebSocketFrame;
import one.papachi.httpd.api.websocket.WebSocketMessage;
import one.papachi.httpd.api.websocket.WebSocketSession;
import one.papachi.httpd.impl.Run;

import java.nio.channels.CompletionHandler;
import java.util.concurrent.Future;

public class DefaultWebSocketSession implements WebSocketSession {

    private final DefaultWebSocketConnection webSocketConnection;

    private WebSocketDataHandler handler;

    public DefaultWebSocketSession(DefaultWebSocketConnection webSocketConnection) {
        this.webSocketConnection = webSocketConnection;
    }

    @Override
    public WebSocketConnection getWebSocketConnection() {
        return webSocketConnection;
    }

    @Override
    public WebSocketDataHandler getHandler() {
        return handler;
    }

    @Override
    public void setHandler(WebSocketDataHandler handler) {
        if (this.handler != null)
            throw new IllegalStateException();
        this.handler = handler;
        Run.async(webSocketConnection);
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public Future<Integer> sendWebSocketMessage(WebSocketMessage message) {
        return null;
    }

    @Override
    public <A> void sendWebSocketMessage(WebSocketMessage message, A attachment, CompletionHandler<Integer, ? super A> handler) {

    }

    @Override
    public Future<Void> sendWebSocketFrame(WebSocketFrame frame) {
        return null;
    }

    @Override
    public <A> void sendWebSocketFrame(WebSocketFrame frame, A attachment, CompletionHandler<Void, ? super A> handler) {

    }

}
