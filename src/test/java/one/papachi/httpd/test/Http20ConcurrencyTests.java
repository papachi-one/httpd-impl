package one.papachi.httpd.test;

import one.papachi.httpd.api.http.HttpClient;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.api.http.HttpServer;
import one.papachi.httpd.api.http.HttpStatus;
import one.papachi.httpd.impl.Run;
import one.papachi.httpd.impl.StandardHttpOptions;
import one.papachi.httpd.impl.Util;
import one.papachi.httpd.impl.http.client.DefaultHttpClient;
import one.papachi.httpd.impl.http.data.DefaultHttpRequest;
import one.papachi.httpd.impl.http.data.DefaultHttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Disabled
public class Http20ConcurrencyTests {

    private static final String protocol = "https";

    private static final String host = "local.papachi.one";

    private static final String file = "/";

    private static final int port = 443;

    private static URL getURL() throws MalformedURLException {
        return new URL(protocol, host, port, file);
    }

    private static HttpServer server;

    private static HttpClient clientA, clientB, clientC, clientD, clientE, clientF, clientG, clientH, clientI, clientJ;

    private static byte[] body;

    @BeforeAll
    static void before() throws UnrecoverableKeyException, CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
//        body = Files.readAllBytes(Path.of("c:\\Users\\PC\\Downloads\\VMware-workstation-full-16.2.4-20089737.exe"));
//        body = Files.readAllBytes(Path.of("c:\\Users\\PC\\Downloads\\SamsungDeXSetupWin.exe"));
        body = Files.readAllBytes(Path.of("c:\\Users\\PC\\Downloads\\15W vs 25W.png"));
        clientA = new DefaultHttpClient();
        clientB = new DefaultHttpClient();
        clientC = new DefaultHttpClient();
        clientD = new DefaultHttpClient();
        clientE = new DefaultHttpClient();
        clientF = new DefaultHttpClient();
        clientG = new DefaultHttpClient();
        clientH = new DefaultHttpClient();
        clientI = new DefaultHttpClient();
        clientJ = new DefaultHttpClient();
        server = getServer();
        server.setHttpHandler(request -> {
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            Run.async(() -> {
                DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
                builder.status(HttpStatus.STATUS_200_OK);
                builder.header("Server", "papachi-httpd/1.0");
                builder.header("Content-Type", "application/octet-stream");
                builder.body(body);
                HttpResponse response = builder.build();
                future.complete(response);
            });
            return future;
        });

        int scale = 1;// 1 - 43.15471698113208, 2 - 69.73170731707317, 3 - 82.27338129496403, 4 - 85.98496240601504, 5 - 89.69411764705882, 6 - 89.69411764705882, 7 - 90.58217821782178
        server.setOption(StandardHttpOptions.WRITE_BUFFER_SIZE, scale * StandardHttpOptions.WRITE_BUFFER_SIZE.defaultValue());
        server.setOption(StandardHttpOptions.MAX_FRAME_SIZE, scale * StandardHttpOptions.MAX_FRAME_SIZE.defaultValue());
        server.setOption(StandardHttpOptions.CONNECTION_WINDOW_SIZE, scale * StandardHttpOptions.CONNECTION_WINDOW_SIZE.defaultValue());
        server.setOption(StandardHttpOptions.STREAM_INITIAL_WINDOW_SIZE, scale * StandardHttpOptions.STREAM_INITIAL_WINDOW_SIZE.defaultValue());
        server.setOption(StandardHttpOptions.CONNECTION_WINDOW_SIZE_THRESHOLD, scale * StandardHttpOptions.CONNECTION_WINDOW_SIZE_THRESHOLD.defaultValue());
        server.setOption(StandardHttpOptions.STREAM_WINDOW_SIZE_THRESHOLD, scale * StandardHttpOptions.STREAM_WINDOW_SIZE_THRESHOLD.defaultValue());

        clientA.setOption(StandardHttpOptions.WRITE_BUFFER_SIZE, scale * StandardHttpOptions.WRITE_BUFFER_SIZE.defaultValue());
        clientA.setOption(StandardHttpOptions.MAX_FRAME_SIZE, scale * StandardHttpOptions.MAX_FRAME_SIZE.defaultValue());
        clientA.setOption(StandardHttpOptions.CONNECTION_WINDOW_SIZE, scale * StandardHttpOptions.CONNECTION_WINDOW_SIZE.defaultValue());
        clientA.setOption(StandardHttpOptions.STREAM_INITIAL_WINDOW_SIZE, scale * StandardHttpOptions.STREAM_INITIAL_WINDOW_SIZE.defaultValue());
        clientA.setOption(StandardHttpOptions.CONNECTION_WINDOW_SIZE_THRESHOLD, scale * StandardHttpOptions.CONNECTION_WINDOW_SIZE_THRESHOLD.defaultValue());
        clientA.setOption(StandardHttpOptions.STREAM_WINDOW_SIZE_THRESHOLD, scale * StandardHttpOptions.STREAM_WINDOW_SIZE_THRESHOLD.defaultValue());

        server.start();
    }

    @AfterAll
    static void after() {
        server.stop();
    }

    private static HttpServer getServer() throws IOException, UnrecoverableKeyException, CertificateException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        HttpServer server = HttpServer.getInstance();
        server.getServerSocketChannel().bind(new InetSocketAddress(port));
        server.setOption(StandardHttpOptions.TLS, Util.getTLSServer());
        return server;
    }

    @RepeatedTest(100)
    @DisplayName("GET / HTTP/2 -> 200 OK")
    void getHttp2a() throws IOException, ExecutionException, InterruptedException, NoSuchAlgorithmException {
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.GET(getURL()).path("/").http2();
        HttpRequest request = builder.build();
        HttpResponse response = clientA.send(request).get();
        String digest = Util.digest(response.getHttpBody());
        Assertions.assertEquals("QfVdjAZFqlPWZ04dtjSuPVAlfNY=", digest);
    }

    @Disabled
    @RepeatedTest(10)
    @DisplayName("GET / HTTP/2 -> 200 OK")
    void getHttp2b() throws MalformedURLException, ExecutionException, InterruptedException {
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.GET(getURL()).path("/").http2();
        HttpRequest request = builder.build();
        HttpResponse response = clientB.send(request).get();
        byte[] responseBody = Util.readBytes(response.getHttpBody());
        Assertions.assertEquals(true, Arrays.equals(body, responseBody));
    }

    @Disabled
    @RepeatedTest(10)
    @DisplayName("GET / HTTP/2 -> 200 OK")
    void getHttp2c() throws MalformedURLException, ExecutionException, InterruptedException {
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.GET(getURL()).path("/").http2();
        HttpRequest request = builder.build();
        HttpResponse response = clientC.send(request).get();
        byte[] responseBody = Util.readBytes(response.getHttpBody());
        Assertions.assertEquals(true, Arrays.equals(body, responseBody));
    }

    @Disabled
    @RepeatedTest(10)
    @DisplayName("GET / HTTP/2 -> 200 OK")
    void getHttp2d() throws MalformedURLException, ExecutionException, InterruptedException {
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.GET(getURL()).path("/").http2();
        HttpRequest request = builder.build();
        HttpResponse response = clientD.send(request).get();
        byte[] responseBody = Util.readBytes(response.getHttpBody());
        Assertions.assertEquals(true, Arrays.equals(body, responseBody));
    }

    @Disabled
    @RepeatedTest(10)
    @DisplayName("GET / HTTP/2 -> 200 OK")
    void getHttp2e() throws MalformedURLException, ExecutionException, InterruptedException {
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.GET(getURL()).path("/").http2();
        HttpRequest request = builder.build();
        HttpResponse response = clientE.send(request).get();
        byte[] responseBody = Util.readBytes(response.getHttpBody());
        Assertions.assertEquals(true, Arrays.equals(body, responseBody));
    }

    @Disabled
    @RepeatedTest(10)
    @DisplayName("GET / HTTP/2 -> 200 OK")
    void getHttp2f() throws MalformedURLException, ExecutionException, InterruptedException {
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.GET(getURL()).path("/").http2();
        HttpRequest request = builder.build();
        HttpResponse response = clientF.send(request).get();
        byte[] responseBody = Util.readBytes(response.getHttpBody());
        Assertions.assertEquals(true, Arrays.equals(body, responseBody));
    }

    @Disabled
    @RepeatedTest(10)
    @DisplayName("GET / HTTP/2 -> 200 OK")
    void getHttp2g() throws MalformedURLException, ExecutionException, InterruptedException {
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.GET(getURL()).path("/").http2();
        HttpRequest request = builder.build();
        HttpResponse response = clientG.send(request).get();
        byte[] responseBody = Util.readBytes(response.getHttpBody());
        Assertions.assertEquals(true, Arrays.equals(body, responseBody));
    }

    @Disabled
    @RepeatedTest(10)
    @DisplayName("GET / HTTP/2 -> 200 OK")
    void getHttp2h() throws MalformedURLException, ExecutionException, InterruptedException {
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.GET(getURL()).path("/").http2();
        HttpRequest request = builder.build();
        HttpResponse response = clientH.send(request).get();
        byte[] responseBody = Util.readBytes(response.getHttpBody());
        Assertions.assertEquals(true, Arrays.equals(body, responseBody));
    }

    @Disabled
    @RepeatedTest(10)
    @DisplayName("GET / HTTP/2 -> 200 OK")
    void getHttp2i() throws MalformedURLException, ExecutionException, InterruptedException {
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.GET(getURL()).path("/").http2();
        HttpRequest request = builder.build();
        HttpResponse response = clientI.send(request).get();
        byte[] responseBody = Util.readBytes(response.getHttpBody());
        Assertions.assertEquals(true, Arrays.equals(body, responseBody));
    }

    @Disabled
    @RepeatedTest(10)
    @DisplayName("GET / HTTP/2 -> 200 OK")
    void getHttp2j() throws MalformedURLException, ExecutionException, InterruptedException {
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.GET(getURL()).path("/").http2();
        HttpRequest request = builder.build();
        HttpResponse response = clientJ.send(request).get();
        byte[] responseBody = Util.readBytes(response.getHttpBody());
        Assertions.assertEquals(true, Arrays.equals(body, responseBody));
    }


}
