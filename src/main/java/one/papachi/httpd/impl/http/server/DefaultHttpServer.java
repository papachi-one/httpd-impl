package one.papachi.httpd.impl.http.server;

import one.papachi.httpd.api.http.HttpHandler;
import one.papachi.httpd.api.http.HttpOption;
import one.papachi.httpd.api.http.HttpServer;
import one.papachi.httpd.api.http.HttpsTLSSupplier;
import one.papachi.httpd.api.websocket.WebSocketHandler;
import one.papachi.httpd.impl.Run;
import one.papachi.httpd.impl.StandardHttpOptions;
import one.papachi.httpd.impl.net.AsynchronousSecureSocketChannel;

import java.io.IOException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DefaultHttpServer implements HttpServer, Runnable {

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    private final AsynchronousServerSocketChannel channel;

    private HttpHandler httpHandler;

    private WebSocketHandler webSocketHandler;

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

    public DefaultHttpServer(AsynchronousServerSocketChannel channel) {
        this.channel = channel;
    }

    @Override
    public ExecutorService getExecutorService() {
        return executorService;
    }

    @Override
    public void start() {
        Run.async(this);
    }

    @Override
    public void stop() {
        try {
            channel.close();
        } catch (IOException e) {
        }
    }

    @Override
    public AsynchronousServerSocketChannel getServerSocketChannel() {
        return channel;
    }

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    @Override
    public void setHttpHandler(HttpHandler handler) {
        this.httpHandler = handler;
    }

    @Override
    public WebSocketHandler getWebSocketHandler() {
        return webSocketHandler;
    }

    @Override
    public void setWebSocketHandler(WebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Override
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
        if (name == StandardHttpOptions.TLS && TLS != null)
            return (T) TLS;
        if (name == StandardHttpOptions.CONNECTION_IDLE_TIMEOUT && CONNECTION_IDLE_TIMEOUT != null)
            return (T) CONNECTION_IDLE_TIMEOUT;
        if (name == StandardHttpOptions.READ_BUFFER_SIZE && READ_BUFFER_SIZE != null)
            return (T) READ_BUFFER_SIZE;
        if (name == StandardHttpOptions.WRITE_BUFFER_SIZE && WRITE_BUFFER_SIZE != null)
            return (T) WRITE_BUFFER_SIZE;
        if (name == StandardHttpOptions.REQUEST_LINE_MAX_LENGTH && REQUEST_LINE_MAX_LENGTH != null)
            return (T) REQUEST_LINE_MAX_LENGTH;
        if (name == StandardHttpOptions.HEADER_LINE_MAX_LENGTH && HEADER_LINE_MAX_LENGTH != null)
            return (T) HEADER_LINE_MAX_LENGTH;
        if (name == StandardHttpOptions.MAX_FRAME_SIZE && MAX_FRAME_SIZE != null)
            return (T) MAX_FRAME_SIZE;
        if (name == StandardHttpOptions.MAX_CONCURRENT_STREAMS && MAX_CONCURRENT_STREAMS != null)
            return (T) MAX_CONCURRENT_STREAMS;
        if (name == StandardHttpOptions.HEADER_TABLE_SIZE && HEADER_TABLE_SIZE != null)
            return (T) HEADER_TABLE_SIZE;
        if (name == StandardHttpOptions.HEADER_LIST_SIZE && HEADER_LIST_SIZE != null)
            return (T) HEADER_LIST_SIZE;
        if (name == StandardHttpOptions.CONNECTION_WINDOW_SIZE && CONNECTION_WINDOW_SIZE != null)
            return (T) CONNECTION_WINDOW_SIZE;
        if (name == StandardHttpOptions.STREAM_INITIAL_WINDOW_SIZE && STREAM_INITIAL_WINDOW_SIZE != null)
            return (T) STREAM_INITIAL_WINDOW_SIZE;
        if (name == StandardHttpOptions.CONNECTION_WINDOW_SIZE_THRESHOLD && CONNECTION_WINDOW_SIZE_THRESHOLD != null)
            return (T) CONNECTION_WINDOW_SIZE_THRESHOLD;
        if (name == StandardHttpOptions.STREAM_WINDOW_SIZE_THRESHOLD && STREAM_WINDOW_SIZE_THRESHOLD != null)
            return (T) STREAM_WINDOW_SIZE_THRESHOLD;
        throw new IllegalArgumentException();
    }

    @Override
    public <T> HttpServer setOption(HttpOption<T> name, T value) {
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

    @Override
    public void run() {
        channel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override
            public void completed(AsynchronousSocketChannel result, Void attachment) {
                channel.accept(attachment, this);
                accepted(result);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
//                exc.printStackTrace();
            }
        });
    }

    private void accepted(AsynchronousSocketChannel channel) {
        String applicationProtocol = "http/1.1";
        if (TLS != null) {
            channel = new AsynchronousSecureSocketChannel(channel, TLS.get());
            try {
                ((AsynchronousSecureSocketChannel) channel).handshake().get();
            } catch (Exception e) {
                close(channel);
                return;
            }
            applicationProtocol = ((AsynchronousSecureSocketChannel) channel).getSslEngine().getApplicationProtocol();
        }
        AsynchronousSocketChannel theChannel = channel;
        switch (applicationProtocol) {
            case "http/1.1" -> executorService.execute(new Http1ServerConnection(theChannel, getHttpHandler()));
            case "h2" -> executorService.execute(() -> new Http2ServerConnection(this, theChannel, getHttpHandler()));
            default -> close(channel);
        }
    }

    private void close(AsynchronousSocketChannel channel) {
        try {
            channel.close();
        } catch (IOException e) {
        }
    }

}
