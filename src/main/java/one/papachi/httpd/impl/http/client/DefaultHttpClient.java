package one.papachi.httpd.impl.http.client;

import one.papachi.httpd.api.http.HttpClient;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class DefaultHttpClient implements HttpClient {

    private final Map<String, List<Http1ClientConnection>> connections = new HashMap<>();

    @Override
    public CompletableFuture<HttpResponse> send(HttpRequest request) {
        Http1ClientConnection connection = getConnection(request.getHeaderValue("Host"));
        System.err.println(connection);
        return connection.send(request);
    }

    private Http1ClientConnection getConnection(String host) {
        return Optional.ofNullable(connections.get(host)).orElse(Collections.emptyList()).stream().filter(Http1ClientConnection::isIdle).findFirst().orElseGet(() -> getNewConnection(host));
    }

    private Http1ClientConnection getNewConnection(String host) {
        try {
            List<Http1ClientConnection> connections = this.connections.get(host);
            if (connections == null) this.connections.put(host, connections = new ArrayList<>());
            Http1ClientConnection connection = new Http1ClientConnection(host);
            connections.add(connection);
            return connection;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
