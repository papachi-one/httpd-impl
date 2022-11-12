package one.papachi.httpd.impl.spi;

import one.papachi.httpd.api.http.HttpServer;
import one.papachi.httpd.api.spi.HttpServerProvider;
import one.papachi.httpd.impl.http.server.DefaultHttpServer;

import java.io.IOException;
import java.nio.channels.AsynchronousServerSocketChannel;

public class DefaultHttpServerProvider implements HttpServerProvider {

    @Override
    public HttpServer getHttpServer() {
        try {
            return new DefaultHttpServer(AsynchronousServerSocketChannel.open());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
