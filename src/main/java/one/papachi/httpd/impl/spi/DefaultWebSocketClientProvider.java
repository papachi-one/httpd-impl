package one.papachi.httpd.impl.spi;

import one.papachi.httpd.api.http.WebSocketClient;
import one.papachi.httpd.api.spi.WebSocketClientProvider;
import one.papachi.httpd.impl.websocket.DefaultWebSocketClient;

public class DefaultWebSocketClientProvider implements WebSocketClientProvider {

    @Override
    public WebSocketClient getWebSocketClient() {
        return new DefaultWebSocketClient();
    }

}
