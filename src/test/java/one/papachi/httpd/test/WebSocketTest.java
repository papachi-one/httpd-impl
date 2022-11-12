package one.papachi.httpd.test;

import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpServer;
import one.papachi.httpd.api.http.HttpsTLSSupplier;
import one.papachi.httpd.api.websocket.WebSocketFrameListener;
import one.papachi.httpd.api.websocket.WebSocketMessageListener;
import one.papachi.httpd.api.websocket.WebSocketSession;
import one.papachi.httpd.api.websocket.WebSocketStreamListener;
import one.papachi.httpd.impl.StandardHttpOptions;
import one.papachi.httpd.impl.Util;

import java.net.InetSocketAddress;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

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
        System.out.println(webSocketSession.getRequest().getPath());
        WebSocketStreamListener streamListener = data -> System.out.println(Util.readString(data));
        WebSocketMessageListener messageListener = data -> System.out.println(Util.readString(data));
        WebSocketFrameListener frameListener = data -> System.out.println(Util.readString(data));

        WebSocketMessageListener messageEchoListener = data -> {
            try {
                if (data == null) {
                    webSocketSession.sendClose();
                    return;
                }
                byte[] bytes = Util.readBytes(data);
                HttpBody src = HttpBody.getBuilder().input(bytes).build();
                webSocketSession.send(src).get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        webSocketSession.setListener(messageEchoListener);
    }

}
