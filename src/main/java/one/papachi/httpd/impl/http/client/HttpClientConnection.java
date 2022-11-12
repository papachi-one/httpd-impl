package one.papachi.httpd.impl.http.client;

import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;

import java.util.concurrent.CompletableFuture;

public interface HttpClientConnection {

    CompletableFuture<HttpResponse> send(HttpRequest request);

    boolean isIdle();

    void close();

}
