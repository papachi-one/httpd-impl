package one.papachi.httpd.impl.http.client;

import one.papachi.httpd.api.http.HttpClient;
import one.papachi.httpd.api.http.HttpOption;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.api.http.HttpsTLSSupplier;
import one.papachi.httpd.impl.StandardHttpOptions;
import one.papachi.httpd.impl.http.Http1ClientConnection;
import one.papachi.httpd.impl.http.Http2ClientConnection;

import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

public class DefaultHttpClient implements HttpClient {

    private final Map<Address, List<Http1ClientConnection>> connections1 = new HashMap<>();

    private final Map<Address, List<Http2ClientConnection>> connections2 = new HashMap<>();

    private HttpsTLSSupplier TLS = null;

    private Integer CONNECTION_IDLE_TIMEOUT = StandardHttpOptions.CONNECTION_IDLE_TIMEOUT.defaultValue();

    private Integer READ_BUFFER_SIZE = StandardHttpOptions.READ_BUFFER_SIZE.defaultValue();

    private Integer WRITE_BUFFER_SIZE = StandardHttpOptions.WRITE_BUFFER_SIZE.defaultValue();

    private Integer REQUEST_LINE_MAX_LENGTH = StandardHttpOptions.REQUEST_LINE_MAX_LENGTH.defaultValue();

    private Integer HEADER_LINE_MAX_LENGTH = StandardHttpOptions.HEADER_LINE_MAX_LENGTH.defaultValue();

    private Integer MAX_FRAME_SIZE = StandardHttpOptions.MAX_FRAME_SIZE.defaultValue();

    private Integer MAX_CONCURRENT_STREAMS = StandardHttpOptions.MAX_CONCURRENT_STREAMS.defaultValue();

    private Integer HEADER_TABLE_SIZE = StandardHttpOptions.HEADER_TABLE_SIZE.defaultValue();

    private Integer HEADER_LIST_SIZE = StandardHttpOptions.HEADER_LIST_SIZE.defaultValue();

    private Integer CONNECTION_WINDOW_SIZE = StandardHttpOptions.CONNECTION_WINDOW_SIZE.defaultValue();

    private Integer STREAM_INITIAL_WINDOW_SIZE = StandardHttpOptions.STREAM_INITIAL_WINDOW_SIZE.defaultValue();

    private Integer CONNECTION_WINDOW_SIZE_THRESHOLD = StandardHttpOptions.CONNECTION_WINDOW_SIZE_THRESHOLD.defaultValue();

    private Integer STREAM_WINDOW_SIZE_THRESHOLD = StandardHttpOptions.STREAM_WINDOW_SIZE_THRESHOLD.defaultValue();

    private record Address(String host, int port, boolean https) {
    }

    @Override
    public CompletableFuture<HttpResponse> send(String host, int port, boolean https, HttpRequest request) {
        Address address = new Address(host, port, https);
        HttpClientConnection connection = getConnection(address);
        return connection.send(request);
    }

    private HttpClientConnection getConnection(Address address) {
        Stream<HttpClientConnection> stream = Stream.concat(
                Optional.ofNullable(connections2.get(address)).orElseGet(Collections::emptyList).stream(),
                Optional.ofNullable(connections1.get(address)).orElseGet(Collections::emptyList).stream());
        HttpClientConnection httpClientConnection = stream.filter(HttpClientConnection::isIdle).findFirst().orElseGet(() -> getNewConnection(address));
        return httpClientConnection;
    }

    private HttpClientConnection getNewConnection(Address address) {
        try {
//            String applicationProtocol = "http/1.1";
//            String applicationProtocol = "h2c";// TODO remove
//            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
//            channel.connect(new InetSocketAddress(address.host, address.port)).get();
//            if (address.https) {
//                channel = new AsynchronousSecureSocketChannel(channel, TLS.get());
//                ((AsynchronousSecureSocketChannel) channel).handshake().get();
//                applicationProtocol = ((AsynchronousSecureSocketChannel) channel).getSslEngine().getApplicationProtocol();
//            }
//            HttpClientConnection connection = null;
//            if ("http/1.1".equals(applicationProtocol)) {
//                connection = new Http1ClientConnection(channel);
//                List<Http1ClientConnection> connections = this.connections1.get(address);
//                if (connections == null) this.connections1.put(address, connections = new ArrayList<>());
//                connections.add((Http1ClientConnection) connection);
//            } else if ("h2".equals(applicationProtocol) || "h2c".equals(applicationProtocol)) {
//                connection = new Http2ClientConnection(this, channel);
//                List<Http2ClientConnection> connections = this.connections2.get(address);
//                if (connections == null) this.connections2.put(address, connections = new ArrayList<>());
//                connections.add((Http2ClientConnection) connection);
//            }
//            return connection;

            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
            channel.connect(new InetSocketAddress(address.host, address.port)).get();

//            Http1ClientConnection connection = new Http1ClientConnection(channel);
//            List<Http1ClientConnection> connections = this.connections1.get(address);
//            if (connections == null) this.connections1.put(address, connections = new ArrayList<>());
//            connections.add((Http1ClientConnection) connection);

            Http2ClientConnection connection = new Http2ClientConnection(channel);
            List<Http2ClientConnection> connections = this.connections2.get(address);
            if (connections == null) this.connections2.put(address, connections = new ArrayList<>());
            connections.add((Http2ClientConnection) connection);

            return connection;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public ExecutorService getExecutorService() {
        return null;
    }

    public Set<HttpOption<?>> supportedOptions() {
        return Set.of(StandardHttpOptions.TLS,
                StandardHttpOptions.CONNECTION_IDLE_TIMEOUT,
                StandardHttpOptions.READ_BUFFER_SIZE,
                StandardHttpOptions.WRITE_BUFFER_SIZE,
                StandardHttpOptions.REQUEST_LINE_MAX_LENGTH,
                StandardHttpOptions.HEADER_LINE_MAX_LENGTH,
                StandardHttpOptions.MAX_FRAME_SIZE,
                StandardHttpOptions.MAX_CONCURRENT_STREAMS,
                StandardHttpOptions.HEADER_TABLE_SIZE,
                StandardHttpOptions.HEADER_LIST_SIZE,
                StandardHttpOptions.CONNECTION_WINDOW_SIZE,
                StandardHttpOptions.STREAM_INITIAL_WINDOW_SIZE,
                StandardHttpOptions.CONNECTION_WINDOW_SIZE_THRESHOLD,
                StandardHttpOptions.STREAM_WINDOW_SIZE_THRESHOLD);
    }

    @Override
    public <T> T getOption(HttpOption<T> name) {
        if (name == StandardHttpOptions.TLS)
            return (T) TLS;
        if (name == StandardHttpOptions.CONNECTION_IDLE_TIMEOUT)
            return (T) CONNECTION_IDLE_TIMEOUT;
        if (name == StandardHttpOptions.READ_BUFFER_SIZE)
            return (T) READ_BUFFER_SIZE;
        if (name == StandardHttpOptions.WRITE_BUFFER_SIZE)
            return (T) WRITE_BUFFER_SIZE;
        if (name == StandardHttpOptions.REQUEST_LINE_MAX_LENGTH)
            return (T) REQUEST_LINE_MAX_LENGTH;
        if (name == StandardHttpOptions.HEADER_LINE_MAX_LENGTH)
            return (T) HEADER_LINE_MAX_LENGTH;
        if (name == StandardHttpOptions.MAX_FRAME_SIZE)
            return (T) MAX_FRAME_SIZE;
        if (name == StandardHttpOptions.MAX_CONCURRENT_STREAMS)
            return (T) MAX_CONCURRENT_STREAMS;
        if (name == StandardHttpOptions.HEADER_TABLE_SIZE)
            return (T) HEADER_TABLE_SIZE;
        if (name == StandardHttpOptions.HEADER_LIST_SIZE)
            return (T) HEADER_LIST_SIZE;
        if (name == StandardHttpOptions.CONNECTION_WINDOW_SIZE)
            return (T) CONNECTION_WINDOW_SIZE;
        if (name == StandardHttpOptions.STREAM_INITIAL_WINDOW_SIZE)
            return (T) STREAM_INITIAL_WINDOW_SIZE;
        if (name == StandardHttpOptions.CONNECTION_WINDOW_SIZE_THRESHOLD)
            return (T) CONNECTION_WINDOW_SIZE_THRESHOLD;
        if (name == StandardHttpOptions.STREAM_WINDOW_SIZE_THRESHOLD)
            return (T) STREAM_WINDOW_SIZE_THRESHOLD;
        throw new IllegalArgumentException();
    }

    @Override
    public <T> HttpClient setOption(HttpOption<T> name, T value) {
        if (name == StandardHttpOptions.TLS)
            TLS = (HttpsTLSSupplier) value;
        else if (name == StandardHttpOptions.CONNECTION_IDLE_TIMEOUT)
            CONNECTION_IDLE_TIMEOUT = (Integer) value;
        else if (name == StandardHttpOptions.READ_BUFFER_SIZE)
            READ_BUFFER_SIZE = (Integer) value;
        else if (name == StandardHttpOptions.WRITE_BUFFER_SIZE)
            WRITE_BUFFER_SIZE = (Integer) value;
        else if (name == StandardHttpOptions.REQUEST_LINE_MAX_LENGTH)
            REQUEST_LINE_MAX_LENGTH = (Integer) value;
        else if (name == StandardHttpOptions.HEADER_LINE_MAX_LENGTH)
            HEADER_LINE_MAX_LENGTH = (Integer) value;
        else if (name == StandardHttpOptions.MAX_FRAME_SIZE)
            MAX_FRAME_SIZE = (Integer) value;
        else if (name == StandardHttpOptions.MAX_CONCURRENT_STREAMS)
            MAX_CONCURRENT_STREAMS = (Integer) value;
        else if (name == StandardHttpOptions.HEADER_TABLE_SIZE)
            HEADER_TABLE_SIZE = (Integer) value;
        else if (name == StandardHttpOptions.HEADER_LIST_SIZE)
            HEADER_LIST_SIZE = (Integer) value;
        else if (name == StandardHttpOptions.CONNECTION_WINDOW_SIZE)
            CONNECTION_WINDOW_SIZE = (Integer) value;
        else if (name == StandardHttpOptions.STREAM_INITIAL_WINDOW_SIZE)
            STREAM_INITIAL_WINDOW_SIZE = (Integer) value;
        else if (name == StandardHttpOptions.CONNECTION_WINDOW_SIZE_THRESHOLD)
            CONNECTION_WINDOW_SIZE_THRESHOLD = (Integer) value;
        else if (name == StandardHttpOptions.STREAM_WINDOW_SIZE_THRESHOLD)
            STREAM_WINDOW_SIZE_THRESHOLD = (Integer) value;

        return this;
    }

}
