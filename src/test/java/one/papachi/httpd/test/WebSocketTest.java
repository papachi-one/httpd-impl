package one.papachi.httpd.test;

import one.papachi.httpd.api.http.HttpServer;
import one.papachi.httpd.api.http.HttpsTLSSupplier;
import one.papachi.httpd.api.websocket.WebSocketFrameHandler;
import one.papachi.httpd.api.websocket.WebSocketMessageHandler;
import one.papachi.httpd.api.websocket.WebSocketSession;
import one.papachi.httpd.api.websocket.WebSocketStreamHandler;
import one.papachi.httpd.impl.StandardHttpOptions;
import one.papachi.httpd.impl.Util;

import java.net.InetSocketAddress;

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
        WebSocketStreamHandler streamHandler = data -> System.out.println(Util.readString(data));
        WebSocketMessageHandler messageHandler = data -> System.out.println(Util.readString(data));
        WebSocketFrameHandler frameHandler = data -> System.out.println(Util.readString(data));
        webSocketSession.setHandler(messageHandler);
    }

}
