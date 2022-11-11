package one.papachi.httpd.test;

import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.api.http.HttpServer;
import one.papachi.httpd.api.http.HttpsTLSSupplier;
import one.papachi.httpd.impl.http.DefaultHttpBody;
import one.papachi.httpd.impl.http.DefaultHttpResponse;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class HttpServerTest {

    public static void main(String[] args) throws Exception {
        HttpsTLSSupplier tls = Util.getTLSServer();
        HttpServer server = HttpServer.getInstance();
        server.getServerSocketChannel().bind(new InetSocketAddress(80));
//        server.setOption(StandardHttpOptions.TLS, tls);
        server.setHttpHandler(HttpServerTest::handle);
        server.start();
        while (true) {
            Thread.sleep(1000);
        }
    }

    static CompletableFuture<HttpResponse> handle(HttpRequest request) {
        CompletableFuture<HttpResponse> completableFuture = new CompletableFuture<>();
        completableFuture.completeAsync(() -> {
            try {
                return process(request);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });
        return completableFuture;
    }

    static HttpResponse process(HttpRequest request) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(request.getMethod()).append(' ').append(request.getPath()).append(' ').append(request.getVersion()).append('\n');
        request.getHeaders().forEach(header -> sb.append(header.getHeaderLine()).append('\n'));
        sb.append('\n');
        System.out.println(sb);
        HttpBody httpBody = request.getHttpBody();
        if (httpBody.isPresent()) {
            ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
            BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream("data.server"));
            while ((httpBody.read(buffer.clear()).get()) != -1) {
                buffer.flip();
                outputStream.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
            }
            outputStream.close();
        }
        DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
        builder.addHeader("Server", "papachi-httpd/1.0");
        builder.addHeader("Content-Type", "application/octet-stream");
        builder.addHeader("Content-Length", "3418040661");
//        builder.setBody(new DefaultHttpBody.DefaultBuilder().setInput(Path.of("c:\\Users\\PC\\Downloads\\15W vs 25W.png")).build());
        builder.setBody(new DefaultHttpBody.DefaultBuilder().setInput(Path.of("c:\\Users\\PC\\Downloads\\fcp2121021.mp4")).build());
//        builder.setBody(sb.toString());
        return builder.build();
    }

}
