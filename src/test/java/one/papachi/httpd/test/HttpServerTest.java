package one.papachi.httpd.test;

import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.api.http.HttpServer;
import one.papachi.httpd.api.http.HttpsTLSSupplier;
import one.papachi.httpd.impl.StandardHttpOptions;
import one.papachi.httpd.impl.http.DefaultHttpResponse;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class HttpServerTest {

    public static void main(String[] args) throws Exception {
        HttpsTLSSupplier tls = Util.getTLS();
        HttpServer server = HttpServer.getInstance();
        server.getServerSocketChannel().bind(new InetSocketAddress(443));
        server.setOption(StandardHttpOptions.TLS, tls);
        server.setHttpHandler(HttpServerTest::handle);
        server.start();
        while (true) {
            Thread.sleep(1000);
        }
    }

    static CompletionStage<HttpResponse> handle(HttpRequest request) {
        CompletableFuture<HttpResponse> completableFuture = new CompletableFuture<>();
        completableFuture.completeAsync(() -> process(request));
        return completableFuture;
    }

    static HttpResponse process(HttpRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.getRequestLine()).append('\n');
        request.getHeaders().forEach(header -> sb.append(header.getHeaderLine()).append('\n'));
        sb.append('\n');
        String body = request.getHttpBody().isPresent() ? Util.readString(request.getHttpBody()) : "";
        sb.append(body);
        DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
        builder.addHeader("Server", "papachi-httpd/1.0").addHeader("Content-Type", "text/plain").addHeader("Connection", "keep-alive");
        builder.setBody(sb.toString());
        return builder.build();
    }

}
