package one.papachi.httpd.test;

import one.papachi.httpd.api.http.HttpServer;
import one.papachi.httpd.api.http.HttpsTLSSupplier;
import one.papachi.httpd.api.websocket.WebSocketFrameListener;
import one.papachi.httpd.api.websocket.WebSocketMessage;
import one.papachi.httpd.api.websocket.WebSocketSession;
import one.papachi.httpd.impl.Run;
import one.papachi.httpd.impl.StandardHttpOptions;
import one.papachi.httpd.impl.Util;
import one.papachi.httpd.impl.websocket.DefaultWebSocketMessage;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

public class WebSocketTest {

    public static void main(String[] args) throws Exception {
        HttpsTLSSupplier tls = Util.getTLSServer();
        HttpServer server = HttpServer.getInstance();
        server.getServerSocketChannel().bind(new InetSocketAddress(443));
        server.setOption(StandardHttpOptions.TLS, tls);
        server.setWebSocketHandler(WebSocketTest::handle);
        server.start();
        while (true) {
            Thread.sleep(1000);
        }
    }

    private static void handle(WebSocketSession webSocketSession) {
        WebSocketFrameListener messageEchoListener = data -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            Run.async(() -> {
                try {
                    if (data == null) {
                        webSocketSession.sendClose();
                        future.complete(null);
                    }
                    byte[] bytes = Util.readBytes(data);
                    System.out.println(new String(bytes));
                    WebSocketMessage message = new DefaultWebSocketMessage.DefaultBuilder().type(WebSocketMessage.Type.TEXT).payload(bytes).build();
                    webSocketSession.send(message).get();
                    future.complete(null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            return future;
        };
        webSocketSession.setListener(messageEchoListener);
    }

}
