package one.papachi.httpd.test;

import one.papachi.httpd.api.http.HttpMethod;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.api.http.HttpServer;
import one.papachi.httpd.api.http.HttpVersion;
import one.papachi.httpd.impl.StandardHttpOptions;
import one.papachi.httpd.impl.Util;
import one.papachi.httpd.impl.http.DefaultHttpRequest;
import one.papachi.httpd.impl.http.DefaultHttpResponse;
import one.papachi.httpd.impl.http.client.DefaultHttpClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Http11Tests {

    private static final String protocol = "https";

    private static final String host = "local.papachi.one";

    private static final String file = "/";

    private static URL getURL(int port) throws MalformedURLException {
        return new URL(protocol, host, port, file);
    }

    private static final String body = "Hello world!";

    private HttpServer getServer() throws IOException, UnrecoverableKeyException, CertificateException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        HttpServer server = HttpServer.getInstance();
        server.getServerSocketChannel().bind(new InetSocketAddress(0));
        server.setOption(StandardHttpOptions.TLS, Util.getTLSServer());
        return server;
    }

    private static int getPort(HttpServer server) throws IOException {
        return ((InetSocketAddress) server.getServerSocketChannel().getLocalAddress()).getPort();
    }

    @RepeatedTest(1)
    @DisplayName("01: GET / HTTP/1.1 -> 200 OK")
    @Timeout(10)
    void getHttp11() throws UnrecoverableKeyException, CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException, ExecutionException, InterruptedException {
        HttpServer server = getServer();
        server.setHttpHandler(request -> {
            logRequest(request);
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.header("Server", "papachi-httpd/1.0");
            builder.header("Content-Type", "text/plain");
            builder.body(body);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        server.start();
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.method(HttpMethod.GET).path("/").version(HttpVersion.HTTP_1_1);
        HttpRequest request = builder.build();
        HttpResponse response = client.send(getURL(getPort(server)), request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(body, responseBody);
        server.stop();
    }

    @RepeatedTest(1)
    @DisplayName("02: GET / HTTP/1.1 -> 200 OK, Content-Length")
    @Timeout(10)
    void getHttp11ContentLengthServer() throws UnrecoverableKeyException, CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException, ExecutionException, InterruptedException {
        HttpServer server = getServer();
        server.setHttpHandler(request -> {
            logRequest(request);
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.header("Server", "papachi-httpd/1.0");
            builder.header("Content-Type", "text/plain");
            builder.header("Content-Length", Integer.toString(body.getBytes().length));
            builder.body(body);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        server.start();
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.method(HttpMethod.GET).path("/").version(HttpVersion.HTTP_1_1);
        HttpRequest request = builder.build();
        HttpResponse response = client.send(getURL(getPort(server)), request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(body, responseBody);
        server.stop();
    }

    @RepeatedTest(1)
    @DisplayName("03: GET / HTTP/1.1 -> 200 OK, Chunked-Encoding")
    @Timeout(10)
    void getHttp11ChunkedEncodingServer() throws UnrecoverableKeyException, CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException, ExecutionException, InterruptedException {
        HttpServer server = getServer();
        server.setHttpHandler(request -> {
            logRequest(request);
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.header("Server", "papachi-httpd/1.0");
            builder.header("Content-Type", "text/plain");
            builder.header("Transfer-Encoding", "chunked");
            builder.body(body);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        server.start();
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.method(HttpMethod.GET).path("/").version(HttpVersion.HTTP_1_1);
        HttpRequest request = builder.build();
        HttpResponse response = client.send(getURL(getPort(server)), request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(body, responseBody);
        server.stop();
    }

    @RepeatedTest(1)
    @DisplayName("04: GET / HTTP/1.1 -> 200 OK, Connection: close")
    @Timeout(10)
    void getHttp11CloseServer() throws UnrecoverableKeyException, CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException, ExecutionException, InterruptedException {
        HttpServer server = getServer();
        server.setHttpHandler(request -> {
            logRequest(request);
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.header("Server", "papachi-httpd/1.0");
            builder.header("Content-Type", "text/plain");
            builder.header("Connection", "close");
            builder.body(body);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        server.start();
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.method(HttpMethod.GET).path("/").version(HttpVersion.HTTP_1_1);
        HttpRequest request = builder.build();
        HttpResponse response = client.send(getURL(getPort(server)), request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(body, responseBody);
        server.stop();
    }

    @RepeatedTest(1)
    @DisplayName("05: GET / HTTP/1.1, Connection: close -> 200 OK")
    @Timeout(10)
    void getHttp11CloseClient() throws UnrecoverableKeyException, CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException, ExecutionException, InterruptedException {
        HttpServer server = getServer();
        server.setHttpHandler(request -> {
            logRequest(request);
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.header("Server", "papachi-httpd/1.0");
            builder.header("Content-Type", "text/plain");
            builder.body(body);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        server.start();
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.method(HttpMethod.GET).path("/").version(HttpVersion.HTTP_1_1);
        builder.header("Connection", "close");
        HttpRequest request = builder.build();
        HttpResponse response = client.send(getURL(getPort(server)), request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(body, responseBody);
        server.stop();
    }

    @RepeatedTest(1)
    @DisplayName("06: GET / HTTP/1.1, Connection: close -> 200 OK, Content-Length")
    @Timeout(10)
    void getHttp11CloseClientContentLengthServer() throws UnrecoverableKeyException, CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException, ExecutionException, InterruptedException {
        HttpServer server = getServer();
        server.setHttpHandler(request -> {
            logRequest(request);
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.header("Server", "papachi-httpd/1.0");
            builder.header("Content-Type", "text/plain");
            builder.header("Content-Length", Integer.toString(body.getBytes().length));
            builder.body(body);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        server.start();
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.method(HttpMethod.GET).path("/").version(HttpVersion.HTTP_1_1);
        builder.header("Connection", "close");
        HttpRequest request = builder.build();
        HttpResponse response = client.send(getURL(getPort(server)), request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(body, responseBody);
        server.stop();
    }

    @RepeatedTest(1)
    @DisplayName("07: GET / HTTP/1.1, Connection: close -> 200 OK, Transfer-Encoding")
    @Timeout(10)
    void getHttp11CloseClientTransferEncodingServer() throws UnrecoverableKeyException, CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException, ExecutionException, InterruptedException {
        HttpServer server = getServer();
        server.setHttpHandler(request -> {
            logRequest(request);
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.header("Server", "papachi-httpd/1.0");
            builder.header("Content-Type", "text/plain");
            builder.header("Transfer-Encoding", "chunked");
            builder.body(body);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        server.start();
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.method(HttpMethod.GET).path("/").version(HttpVersion.HTTP_1_1);
        builder.header("Connection", "close");
        HttpRequest request = builder.build();
        HttpResponse response = client.send(getURL(getPort(server)), request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(body, responseBody);
        server.stop();
    }

    @RepeatedTest(1)
    @DisplayName("16: POST / HTTP/1.1 -> 200 OK")
    @Timeout(10)
    void postHttp11() throws UnrecoverableKeyException, CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException, ExecutionException, InterruptedException {
        HttpServer server = getServer();
        server.setHttpHandler(request -> {
            logRequest(request);
            String requestBody = Util.readString(request.getHttpBody());
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.header("Server", "papachi-httpd/1.0");
            builder.header("Content-Type", "text/plain");
            builder.body(requestBody);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        server.start();
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.method(HttpMethod.GET).path("/").version(HttpVersion.HTTP_1_1);
        builder.body(body);
        HttpRequest request = builder.build();
        HttpResponse response = client.send(getURL(getPort(server)), request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(body, responseBody);
        server.stop();
    }

    @RepeatedTest(1)
    @DisplayName("16: POST / HTTP/1.1, Content-Length -> 200 OK")
    @Timeout(10)
    void postHttp11ContentLengthClient() throws UnrecoverableKeyException, CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException, ExecutionException, InterruptedException {
        HttpServer server = getServer();
        server.setHttpHandler(request -> {
            logRequest(request);
            String requestBody = Util.readString(request.getHttpBody());
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.header("Server", "papachi-httpd/1.0");
            builder.header("Content-Type", "text/plain");
            builder.body(requestBody);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        server.start();
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.method(HttpMethod.GET).path("/").version(HttpVersion.HTTP_1_1);
        builder.body(body);
        builder.header("Content-Length", Integer.toString(body.getBytes().length));
        HttpRequest request = builder.build();
        HttpResponse response = client.send(getURL(getPort(server)), request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(body, responseBody);
        server.stop();
    }

    @RepeatedTest(1)
    @DisplayName("17: POST / HTTP/1.1 -> 200 OK, Content-Length")
    @Timeout(10)
    void postHttp11ContentLengthServer() throws UnrecoverableKeyException, CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException, ExecutionException, InterruptedException {
        HttpServer server = getServer();
        server.setHttpHandler(request -> {
            logRequest(request);
            String requestBody = Util.readString(request.getHttpBody());
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.header("Server", "papachi-httpd/1.0");
            builder.header("Content-Type", "text/plain");
            builder.header("Content-Length", Integer.toString(requestBody.getBytes().length));
            builder.body(requestBody);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        server.start();
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.method(HttpMethod.GET).path("/").version(HttpVersion.HTTP_1_1);
        builder.body(body);
        HttpRequest request = builder.build();
        HttpResponse response = client.send(getURL(getPort(server)), request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(body, responseBody);
        server.stop();
    }

    @RepeatedTest(1)
    @DisplayName("18: POST / HTTP/1.1, Content-Length -> 200 OK, Content-Length")
    @Timeout(10)
    void postHttp11ContentLengthClientServer() throws UnrecoverableKeyException, CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException, ExecutionException, InterruptedException {
        HttpServer server = getServer();
        server.setHttpHandler(request -> {
            logRequest(request);
            String requestBody = Util.readString(request.getHttpBody());
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.header("Server", "papachi-httpd/1.0");
            builder.header("Content-Type", "text/plain");
            builder.header("Content-Length", Integer.toString(requestBody.getBytes().length));
            builder.body(requestBody);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        server.start();
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.method(HttpMethod.GET).path("/").version(HttpVersion.HTTP_1_1);
        builder.header("Content-Length", Integer.toString(body.getBytes().length));
        builder.body(body);
        HttpRequest request = builder.build();
        HttpResponse response = client.send(getURL(getPort(server)), request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(body, responseBody);
        server.stop();
    }

    private static void logRequest(HttpRequest request) {
        System.out.println("Request:");
        System.out.println(request.getMethod() + " " + request.getPath() + " " + request.getVersion());
        request.getHeaders().forEach(System.out::println);
        System.out.println();
    }

    private static void logResponse(HttpResponse response) {
        System.out.println("Response:");
        System.out.println(response.getVersion() + " " + response.getStatusCode() + " " + response.getReasonPhrase());
        response.getHeaders().forEach(System.out::println);
        System.out.println();
    }

}
