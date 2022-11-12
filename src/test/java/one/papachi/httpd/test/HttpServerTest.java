package one.papachi.httpd.test;

import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.api.http.HttpServer;
import one.papachi.httpd.impl.Run;
import one.papachi.httpd.impl.StandardHttpOptions;
import one.papachi.httpd.impl.Util;
import one.papachi.httpd.impl.http.DefaultHttpResponse;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class HttpServerTest {

    public static void main(String[] args) throws Exception {
        byte[] body = Files.readAllBytes(Path.of("c:\\Users\\PC\\Downloads\\VMware-workstation-full-16.2.4-20089737.exe"));
        HttpServer server = HttpServer.getInstance();
        server.getServerSocketChannel().bind(new InetSocketAddress(443));
        server.setOption(StandardHttpOptions.TLS, Util.getTLSServer());
//        server.setOption(StandardHttpOptions.WRITE_BUFFER_SIZE, 4 * StandardHttpOptions.WRITE_BUFFER_SIZE.defaultValue());
//        server.setOption(StandardHttpOptions.MAX_FRAME_SIZE, 4 * StandardHttpOptions.MAX_FRAME_SIZE.defaultValue());
//        server.setOption(StandardHttpOptions.CONNECTION_WINDOW_SIZE, 4 * StandardHttpOptions.CONNECTION_WINDOW_SIZE.defaultValue());
//        server.setOption(StandardHttpOptions.STREAM_INITIAL_WINDOW_SIZE, 4 * StandardHttpOptions.STREAM_INITIAL_WINDOW_SIZE.defaultValue());
//        server.setOption(StandardHttpOptions.CONNECTION_WINDOW_SIZE_THRESHOLD, 4 * StandardHttpOptions.CONNECTION_WINDOW_SIZE_THRESHOLD.defaultValue());
//        server.setOption(StandardHttpOptions.STREAM_WINDOW_SIZE_THRESHOLD, 4 * StandardHttpOptions.STREAM_WINDOW_SIZE_THRESHOLD.defaultValue());
        server.setHttpHandler(request -> {
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            Run.async(() -> {
                DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
                builder.header("Server", "papachi-httpd/1.0");
                builder.header("Content-Type", "application/octet-stream");
                builder.body(body);
                HttpResponse response = builder.build();
                future.complete(response);
            });
            return future;
        });
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
        builder.header("Server", "papachi-httpd/1.0");
        builder.header("Content-type", "text/plain");
//        builder.addHeader("Content-Type", "application/octet-stream");
//        builder.addHeader("Content-Length", "3418040661");
//        builder.setBody(new DefaultHttpBody.DefaultBuilder().setInput(Path.of("c:\\Users\\PC\\Downloads\\15W vs 25W.png")).build());
//        builder.setBody(new DefaultHttpBody.DefaultBuilder().setInput(Path.of("c:\\Users\\PC\\Downloads\\fcp2121021.mp4")).build());
        builder.body(sb.toString());
        return builder.build();
    }

}
