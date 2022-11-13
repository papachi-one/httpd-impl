package one.papachi.httpd.impl.http.client;

import one.papachi.httpd.api.http.HttpClient;
import one.papachi.httpd.api.http.HttpOption;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.api.http.HttpVersion;
import one.papachi.httpd.api.http.HttpsTLSSupplier;
import one.papachi.httpd.impl.StandardHttpOptions;
import one.papachi.httpd.impl.Util;
import one.papachi.httpd.impl.net.AsynchronousSecureSocketChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
    public void close() {
        Stream.concat(connections1.values().stream().flatMap(Collection::stream), connections2.values().stream().flatMap(Collection::stream)).forEach(HttpClientConnection::close);
    }

    @Override
    public CompletableFuture<HttpResponse> send(HttpRequest request) {
        InetSocketAddress server = request.getServer();
        String host = server.getHostString();
        int port = server.getPort();
        String scheme = request.getScheme();
        HttpVersion version = request.getVersion();
        Address address = new Address(host, port, "https".equalsIgnoreCase(scheme));
        HttpClientConnection connection = null;
        try {
            connection = getConnection(address, version);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return connection.send(request);
    }

    synchronized protected HttpClientConnection getConnection(Address address, HttpVersion version) throws IOException, ExecutionException, InterruptedException, NoSuchAlgorithmException, KeyManagementException {
        if (version == HttpVersion.AUTO) {
            Stream<HttpClientConnection> connections = Stream.concat(Optional.ofNullable(connections2.get(address)).orElseGet(Collections::emptyList).stream(), Optional.ofNullable(connections1.get(address)).orElseGet(Collections::emptyList).stream());
            HttpClientConnection connection = connections.filter(HttpClientConnection::isIdle).findFirst().orElse(null);
            if (connection == null) {
                AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
                channel.connect(new InetSocketAddress(address.host, address.port)).get();
                if (address.https) {
                    channel = new AsynchronousSecureSocketChannel(channel, Util.getTLSClientHttp2And1().get());
                    ((AsynchronousSecureSocketChannel) channel).handshake().get();
                    String protocol = ((AsynchronousSecureSocketChannel) channel).getSslEngine().getApplicationProtocol();
                    connection = switch (protocol) {
                        case "h2" -> new Http2ClientConnection(this, channel);
                        default -> new Http1ClientConnection(channel);
                    };
                    if (connection instanceof Http1ClientConnection c) {
                        List<Http1ClientConnection> list = connections1.get(address);
                        if (list == null) {
                            connections1.put(address, list = new ArrayList<>());
                        }
                        list.add(c);
                    } else if (connection instanceof Http2ClientConnection c) {
                        List<Http2ClientConnection> list = connections2.get(address);
                        if (list == null) {
                            connections2.put(address, list = new ArrayList<>());
                        }
                        list.add(c);
                    }
                } else {
                    connection = new Http1ClientConnection(channel);
                    List<Http1ClientConnection> list = this.connections1.get(address);
                    if (connections == null) this.connections1.put(address, list = new ArrayList<>());
                    list.add((Http1ClientConnection) connection);
                }
            }
            return connection;
        } else if (version == HttpVersion.HTTP_2) {
            Stream<Http2ClientConnection> connections = Optional.ofNullable(connections2.get(address)).orElseGet(Collections::emptyList).stream();
            Http2ClientConnection connection = connections.filter(HttpClientConnection::isIdle).findFirst().orElse(null);
            if (connection == null) {
                AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
                channel.connect(new InetSocketAddress(address.host, address.port)).get();
                if (address.https) {
                    channel = new AsynchronousSecureSocketChannel(channel, Util.getTLSClientHttp2().get());
                    ((AsynchronousSecureSocketChannel) channel).handshake().get();
                    String protocol = ((AsynchronousSecureSocketChannel) channel).getSslEngine().getApplicationProtocol();
                    connection = switch (protocol) {
                        case "h2" -> new Http2ClientConnection(this, channel);
                        default -> null;
                    };
                    List<Http2ClientConnection> list = connections2.get(address);
                    if (list == null) {
                        connections2.put(address, list = new ArrayList<>());
                    }
                    list.add(connection);
                }
            }
            return connection;
        } else if (version == HttpVersion.HTTP_1_1) {
            Stream<Http1ClientConnection> connections = Optional.ofNullable(connections1.get(address)).orElseGet(Collections::emptyList).stream();
            HttpClientConnection connection = connections.filter(HttpClientConnection::isIdle).findFirst().orElse(null);
            if (connection == null) {
                AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
                channel.connect(new InetSocketAddress(address.host, address.port)).get();
                if (address.https) {
                    channel = new AsynchronousSecureSocketChannel(channel, Util.getTLSClientHttp1().get());
                    ((AsynchronousSecureSocketChannel) channel).handshake().get();
                    String protocol = ((AsynchronousSecureSocketChannel) channel).getSslEngine().getApplicationProtocol();
                    connection = switch (protocol) {
                        case "http/1.1" -> new Http1ClientConnection(channel);
                        default -> null;
                    };
                }
            }
            return connection;
        } else if (version == HttpVersion.HTTP_1_0) {
            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
            channel.connect(new InetSocketAddress(address.host, address.port)).get();
            if (address.https) {
                channel = new AsynchronousSecureSocketChannel(channel, Util.getTLSClientHttp1().get());
                ((AsynchronousSecureSocketChannel) channel).handshake().get();
                String protocol = ((AsynchronousSecureSocketChannel) channel).getSslEngine().getApplicationProtocol();
                if (protocol == "http/1.1") {
                    return new Http1ClientConnection(channel);
                }
            }
            return new Http1ClientConnection(channel);
        } else {
            return null;
        }
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
