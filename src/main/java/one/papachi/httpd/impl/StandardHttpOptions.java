package one.papachi.httpd.impl;


import one.papachi.httpd.api.http.HttpOption;
import one.papachi.httpd.api.http.HttpsTLSSupplier;

public final class StandardHttpOptions {

    public static final HttpOption<Boolean> WEBSOCKET = new StdHttpOption<>("WEBSOCKET", Boolean.class, false);// HTTP/1.1
    public static final HttpOption<Boolean> HTTP_2 = new StdHttpOption<>("HTTP_2", Boolean.class, false);// HTTP/2
    public static final HttpOption<HttpsTLSSupplier> TLS = new StdHttpOption<>("TLS", HttpsTLSSupplier.class, null);// HTTP/1.1, HTTP/2
    public static final HttpOption<Integer> CONNECTION_IDLE_TIMEOUT = new StdHttpOption<>("CONNECTION_IDLE_TIMEOUT", Integer.class, 60);// HTTP/1.1, HTTP/2
    public static final HttpOption<Integer> READ_BUFFER_SIZE = new StdHttpOption<>("READ_BUFFER_SIZE", Integer.class, 32 * 1024);// HTTP/1.1, HTTP/2
    public static final HttpOption<Boolean> HTTP2_READ_BUFFER_PASS_TROUGH = new StdHttpOption<>("HTTP2_READ_BUFFER_PASS_TROUGH", Boolean.class, false);// HTTP/2
    public static final HttpOption<Integer> WRITE_BUFFER_SIZE = new StdHttpOption<>("WRITE_BUFFER_SIZE", Integer.class, 32 * 1024);// HTTP/1.1, HTTP/2
    public static final HttpOption<Integer> REQUEST_LINE_MAX_LENGTH = new StdHttpOption<>("REQUEST_LINE_MAX_LENGTH", Integer.class, 32 * 1024);// HTTP/1.1
    public static final HttpOption<Integer> HEADER_LINE_MAX_LENGTH = new StdHttpOption<>("HEADER_LINE_MAX_LENGTH", Integer.class, 32 * 1024);// HTTP/1.1
    public static final HttpOption<Integer> MAX_FRAME_SIZE = new StdHttpOption<>("MAX_FRAME_SIZE", Integer.class, 16384);//HTTP/2
    public static final HttpOption<Integer> MAX_CONCURRENT_STREAMS = new StdHttpOption<>("MAX_CONCURRENT_STREAMS", Integer.class, Integer.MAX_VALUE);// HTTP/2
    public static final HttpOption<Integer> HEADER_TABLE_SIZE = new StdHttpOption<>("HEADER_TABLE_SIZE", Integer.class, 4096);//HTTP/2
    public static final HttpOption<Integer> HEADER_LIST_SIZE = new StdHttpOption<>("HEADER_LIST_SIZE", Integer.class, Integer.MAX_VALUE);//HTTP/2
    public static final HttpOption<Integer> CONNECTION_WINDOW_SIZE = new StdHttpOption<>("CONNECTION_RCV_WINDOW_SIZE", Integer.class, 65535);// HTTP/2
    public static final HttpOption<Integer> STREAM_INITIAL_WINDOW_SIZE = new StdHttpOption<>("STREAM_RCV_WINDOW_SIZE", Integer.class, 65535);// HTTP/2
    public static final HttpOption<Integer> CONNECTION_WINDOW_SIZE_THRESHOLD = new StdHttpOption<>("CONNECTION_WINDOW_SIZE_THRESHOLD", Integer.class, 32768);// HTTP/2
    public static final HttpOption<Integer> STREAM_WINDOW_SIZE_THRESHOLD = new StdHttpOption<>("STREAM_WINDOW_SIZE_THRESHOLD", Integer.class, 32768);// HTTP/2

    private record StdHttpOption<T>(String name, Class<T> type, T defaultValue) implements HttpOption<T> {}

}
