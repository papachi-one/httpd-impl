package one.papachi.httpd.test;

import one.papachi.httpd.api.http.HttpMethod;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.api.http.HttpServer;
import one.papachi.httpd.api.http.HttpVersion;
import one.papachi.httpd.impl.http.DefaultHttpRequest;
import one.papachi.httpd.impl.http.DefaultHttpResponse;
import one.papachi.httpd.impl.http.client.DefaultHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Http1Tests {

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.getInstance();
        AsynchronousServerSocketChannel serverSocketChannel = server.getServerSocketChannel();
        serverSocketChannel.bind(new InetSocketAddress(0));
        port = ((InetSocketAddress) serverSocketChannel.getLocalAddress()).getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    @DisplayName("01: GET / HTTP/1.0 ; 200 OK")
    @Timeout(1)
    void getHttp10() throws ExecutionException, InterruptedException {
        String preparedResponseBody = "Hello world!";
        server.setHttpHandler(request -> {
            logRequest(request);
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.addHeader("Server", "papachi-httpd/1.0");
            builder.addHeader("Content-Type", "text/plain");
            builder.setBody(preparedResponseBody);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.setMethod(HttpMethod.GET).setPath("/").setVersion(HttpVersion.HTTP_1_0);
        HttpRequest request = builder.build();
        HttpResponse response = client.send("local.papachi.one", port, false, request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(preparedResponseBody, responseBody);
    }

    @Test
    @DisplayName("02: GET / HTTP/1.0 ; 200 OK, Content-Length: 12")
    @Timeout(1)
    void getHttp10ContentLength() throws ExecutionException, InterruptedException {
        String preparedResponseBody = "Hello world!";
        server.setHttpHandler(request -> {
            logRequest(request);
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.addHeader("Server", "papachi-httpd/1.0");
            builder.addHeader("Content-Type", "text/plain");
            builder.addHeader("Content-Length", "12");
            builder.setBody(preparedResponseBody);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.setMethod(HttpMethod.GET).setPath("/").setVersion(HttpVersion.HTTP_1_0);
        HttpRequest request = builder.build();
        HttpResponse response = client.send("local.papachi.one", port, false, request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(preparedResponseBody, responseBody);
    }

    @Test
    @DisplayName("03: POST / HTTP/1.0 ; 411 Length Required")
    @Timeout(1)
    void postHttp10LengthRequired() throws ExecutionException, InterruptedException {
        String preparedRequestBody = "Hello world!";
        server.setHttpHandler(request -> {
            logRequest(request);
            String requestBody = Util.readString(request.getHttpBody());
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.addHeader("Server", "papachi-httpd/1.0");
            builder.setBody(requestBody);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.setMethod(HttpMethod.POST).setPath("/").setVersion(HttpVersion.HTTP_1_0);
        builder.setBody(preparedRequestBody);
        HttpRequest request = builder.build();
        HttpResponse response = client.send("local.papachi.one", port, false, request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(411, response.getStatusCode());
    }

    @Test
    @DisplayName("04: POST / HTTP/1.0, Content-Length: 12; 200 OK")
    @Timeout(1)
    void postHttp10ContentLength() throws ExecutionException, InterruptedException {
        String preparedRequestBody = "Hello world!";
        server.setHttpHandler(request -> {
            logRequest(request);
            String requestBody = Util.readString(request.getHttpBody());
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.addHeader("Server", "papachi-httpd/1.0");
            builder.setBody(requestBody);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.setMethod(HttpMethod.POST).setPath("/").setVersion(HttpVersion.HTTP_1_0);
        builder.addHeader("Content-Length", "12");
        builder.setBody(preparedRequestBody);
        HttpRequest request = builder.build();
        HttpResponse response = client.send("local.papachi.one", port, false, request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(preparedRequestBody, responseBody);
    }

    @Test
    @DisplayName("05: GET / HTTP/1.1 ; 200 OK")
    @Timeout(1)
    void getHttp11() throws ExecutionException, InterruptedException {
        String preparedResponseBody = "Hello world!";
        server.setHttpHandler(request -> {
            logRequest(request);
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.addHeader("Server", "papachi-httpd/1.0");
            builder.addHeader("Content-Length", Integer.toString(preparedResponseBody.length()));
            builder.setBody(preparedResponseBody);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.setMethod(HttpMethod.GET).setPath("/").setVersion(HttpVersion.HTTP_1_1);
        HttpRequest request = builder.build();
        HttpResponse response = client.send("local.papachi.one", port, false, request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(preparedResponseBody, responseBody);
    }

    @Test
    @DisplayName("06: GET / HTTP/1.1 ; 204 No Content")
    @Timeout(1)
    void getHttp11NoContent() throws ExecutionException, InterruptedException {
        server.setHttpHandler(request -> {
            logRequest(request);
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.setStatusCode(204 );
            builder.setReasonPhrase("No Content");
            builder.addHeader("Server", "papachi-httpd/1.0");
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.setMethod(HttpMethod.GET).setPath("/").setVersion(HttpVersion.HTTP_1_1);
        HttpRequest request = builder.build();
        HttpResponse response = client.send("local.papachi.one", port, false, request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals("", responseBody);
    }

    @Test
    @DisplayName("07: GET / HTTP/1.1 ; 304 Not Modified")
    @Timeout(1)
    void getHttp11NotModified() throws ExecutionException, InterruptedException {
        server.setHttpHandler(request -> {
            logRequest(request);
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.setStatusCode(304);
            builder.setReasonPhrase("Not Modified");
            builder.addHeader("Server", "papachi-httpd/1.0");
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.setMethod(HttpMethod.GET).setPath("/").setVersion(HttpVersion.HTTP_1_1);
        HttpRequest request = builder.build();
        HttpResponse response = client.send("local.papachi.one", port, false, request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals("", responseBody);
    }

    @Test
    @DisplayName("08: GET / HTTP/1.1, Connection: close; 200 OK")
    @Timeout(1)
    void getHttp11CloseClient() throws ExecutionException, InterruptedException {
        String preparedResponseBody = "Hello world!";
        server.setHttpHandler(request -> {
            logRequest(request);
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.addHeader("Server", "papachi-httpd/1.0");
            builder.setBody(preparedResponseBody);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.setMethod(HttpMethod.GET).setPath("/").setVersion(HttpVersion.HTTP_1_1);
        builder.addHeader("Connection", "close");
        HttpRequest request = builder.build();
        HttpResponse response = client.send("local.papachi.one", port, false, request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(preparedResponseBody, responseBody);
    }

    @Test
    @DisplayName("09: GET / HTTP/1.1 ; 200 OK, Connection: close")
    @Timeout(1)
    void getHttp11CloseServer() throws ExecutionException, InterruptedException {
        String preparedResponseBody = "Hello world!";
        server.setHttpHandler(request -> {
            logRequest(request);
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.addHeader("Server", "papachi-httpd/1.0");
            builder.addHeader("Connection", "close");
            builder.setBody(preparedResponseBody);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.setMethod(HttpMethod.GET).setPath("/").setVersion(HttpVersion.HTTP_1_1);
        HttpRequest request = builder.build();
        HttpResponse response = client.send("local.papachi.one", port, false, request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(preparedResponseBody, responseBody);
    }

    @Test
    @DisplayName("10: GET / HTTP/1.1 ; 200 OK, Content-Length: 12")
    @Timeout(1)
    void getHttp11ContentLength() throws ExecutionException, InterruptedException {
        String preparedResponseBody = "Hello world!";
        server.setHttpHandler(request -> {
            logRequest(request);
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.addHeader("Server", "papachi-httpd/1.0");
            builder.addHeader("Content-Length", Integer.toString(preparedResponseBody.length()));
            builder.setBody(preparedResponseBody);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.setMethod(HttpMethod.GET).setPath("/").setVersion(HttpVersion.HTTP_1_1);
        HttpRequest request = builder.build();
        HttpResponse response = client.send("local.papachi.one", port, false, request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(preparedResponseBody, responseBody);
    }

    @Test
    @DisplayName("11: GET / HTTP/1.1 ; 200 OK, Transfer-Encoding: chunked")
    @Timeout(1)
    void getHttp11Chunked() throws ExecutionException, InterruptedException {
        String preparedResponseBody = "Hello world!";
        server.setHttpHandler(request -> {
            logRequest(request);
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.addHeader("Server", "papachi-httpd/1.0");
            builder.addHeader("Transfer-Encoding", "chunked");
            builder.setBody(preparedResponseBody);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.setMethod(HttpMethod.GET).setPath("/").setVersion(HttpVersion.HTTP_1_1);
        HttpRequest request = builder.build();
        HttpResponse response = client.send("local.papachi.one", port, false, request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(preparedResponseBody, responseBody);
    }

    @Test
    @DisplayName("12: POST / HTTP/1.1 ; 200 OK")
    @Timeout(1)
    void postHttp11() throws ExecutionException, InterruptedException {
        String preparedRequestBody = "Hello world!";
        server.setHttpHandler(request -> {
            logRequest(request);
            String requestBody = Util.readString(request.getHttpBody());
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.addHeader("Server", "papachi-httpd/1.0");
            builder.setBody(requestBody);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.setMethod(HttpMethod.POST).setPath("/").setVersion(HttpVersion.HTTP_1_1);
        builder.setBody(preparedRequestBody);
        HttpRequest request = builder.build();
        HttpResponse response = client.send("local.papachi.one", port, false, request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(preparedRequestBody, responseBody);
    }

    @Test
    @DisplayName("13: POST / HTTP/1.1, Content-Length: 12; 200 OK")
    @Timeout(1)
    void postHttp11ContentLength() throws ExecutionException, InterruptedException {
        String preparedRequestBody = "Hello world!";
        server.setHttpHandler(request -> {
            logRequest(request);
            String requestBody = Util.readString(request.getHttpBody());
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.addHeader("Server", "papachi-httpd/1.0");
            builder.setBody(requestBody);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.setMethod(HttpMethod.POST).setPath("/").setVersion(HttpVersion.HTTP_1_1);
        builder.addHeader("Content-Length", "12");
        builder.setBody(preparedRequestBody);
        HttpRequest request = builder.build();
        HttpResponse response = client.send("local.papachi.one", port, false, request).get();
        logResponse(response);
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(preparedRequestBody, responseBody);
    }

    @Test
    @DisplayName("14: POST / HTTP/1.1, Transfer-Encoding: chunked; 200 OK")
    @Timeout(1)
    void postHttp11Chunked() throws ExecutionException, InterruptedException {
        String preparedRequestBody = "Hello world!";
        server.setHttpHandler(request -> {
            String requestBody = Util.readString(request.getHttpBody());
            logRequest(request);
            System.out.println(requestBody);
            System.out.println();
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.addHeader("Server", "papachi-httpd/1.0");
            builder.setBody(requestBody);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.setMethod(HttpMethod.POST).setPath("/").setVersion(HttpVersion.HTTP_1_1);
        builder.addHeader("Transfer-Encoding", "chunked");
        builder.setBody(preparedRequestBody);
        HttpRequest request = builder.build();
        HttpResponse response = client.send("local.papachi.one", port, false, request).get();
        String responseBody = Util.readString(response.getHttpBody());
        logResponse(response);
        System.out.println(responseBody);
        Assertions.assertEquals(preparedRequestBody, responseBody);
    }

    @Test
    @Disabled
    void getHttp2() throws ExecutionException, InterruptedException {
        String preparedResponseBody = "Hello world!";
        server.setHttpHandler(request -> {
            DefaultHttpResponse.DefaultBuilder builder = new DefaultHttpResponse.DefaultBuilder();
            builder.addHeader("Server", "papachi-httpd/1.0");
            builder.addHeader("Content-Type", "text/plain");
            builder.setBody(preparedResponseBody);
            HttpResponse response = builder.build();
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            future.complete(response);
            return future;
        });
        DefaultHttpClient client = new DefaultHttpClient();
        DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
        builder.setMethod(HttpMethod.GET).setPath("/").setVersion(HttpVersion.HTTP_2);
        HttpRequest request = builder.build();
        HttpResponse response = client.send("local.papachi.one", port, false, request).get();
        String responseBody = Util.readString(response.getHttpBody());
        Assertions.assertEquals(preparedResponseBody, responseBody);
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
