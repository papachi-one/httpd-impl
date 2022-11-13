package one.papachi.httpd.impl.websocket;

import one.papachi.httpd.api.http.HttpVersion;
import one.papachi.httpd.api.http.WebSocketClient;
import one.papachi.httpd.api.websocket.WebSocketRequest;
import one.papachi.httpd.api.websocket.WebSocketSession;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

public class DefaultWebSocketClient implements WebSocketClient {

    @Override
    public CompletableFuture<WebSocketSession> connect(WebSocketRequest request) {
        InetSocketAddress server = request.getServer();
        String host = server.getHostString();
        int port = server.getPort();
        String scheme = request.getScheme();
        HttpVersion version = request.getVersion();

        // TODO

        return null;
    }

}
