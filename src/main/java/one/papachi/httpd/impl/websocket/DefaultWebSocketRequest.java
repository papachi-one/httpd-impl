package one.papachi.httpd.impl.websocket;

import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpHeaders;
import one.papachi.httpd.api.http.HttpMethod;
import one.papachi.httpd.api.http.HttpVersion;
import one.papachi.httpd.api.websocket.WebSocketRequest;
import one.papachi.httpd.impl.http.data.DefaultHttpHeaders;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DefaultWebSocketRequest implements WebSocketRequest {

    public static class DefaultBuilder implements WebSocketRequest.Builder {

        protected InetSocketAddress server;

        protected String scheme;

        protected HttpMethod method = HttpMethod.GET;

        protected String path = "/";

        protected HttpVersion version = HttpVersion.HTTP_1_1;

        protected final Map<String, List<String>> parameters = new LinkedHashMap<>();

        protected final HttpHeaders.Builder headersBuilder = new DefaultHttpHeaders.DefaultBuilder();

        @Override
        public Builder server(InetSocketAddress server) {
            this.server = server;
            return this;
        }

        @Override
        public Builder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        @Override
        public Builder method(HttpMethod method) {
            if (method != HttpMethod.GET)
                throw new IllegalArgumentException();
            this.method = method;
            return this;
        }

        @Override
        public Builder path(String path) {
            parameters.clear();
            String[] split1 = path.split("\\?", 2);
            String[] split2 = split1.length == 2 ? split1[1].split("&") : new String[0];
            for (String string : split2) {
                String[] split3 = string.split("=", 2);
                String name = URLDecoder.decode(split3[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(split3.length == 2 ? split3[1] : "", StandardCharsets.UTF_8);
                parameter(name, value);
            }
            this.path = split1[0];
            return this;
        }

        @Override
        public Builder version(HttpVersion version) {
            if (version == HttpVersion.HTTP_1_0)
                throw new IllegalArgumentException();
            this.version = version;
            return this;
        }

        @Override
        public Builder parameter(String name, String value) {
            parameters.compute(name, (k, v) -> {
                v = v != null ? v : new LinkedList<>();
                v.add(value);
                return v;
            });
            return this;
        }

        @Override
        public Builder headerLine(String line) {
            headersBuilder.headerLine(line);
            return this;
        }

        @Override
        public Builder header(HttpHeader header) {
            headersBuilder.header(header);
            return this;
        }

        @Override
        public Builder header(String name, String value) {
            headersBuilder.header(name, value);
            return this;
        }

        @Override
        public WebSocketRequest build() {
            return new DefaultWebSocketRequest(server, scheme, method, path, version, parameters, headersBuilder.build());
        }

    }

    protected final InetSocketAddress server;

    protected final String scheme;

    protected final HttpMethod method;

    protected final String path;

    protected final HttpVersion version;

    protected final Map<String, List<String>> parameters;

    protected final HttpHeaders headers;

    protected DefaultWebSocketRequest(InetSocketAddress server, String scheme, HttpMethod method, String path, HttpVersion version, Map<String, List<String>> parameters, HttpHeaders headers) {
        this.server = server;
        this.scheme = scheme;
        this.method = method;
        this.path = path;
        this.version = version;
        this.parameters = parameters;
        this.headers = headers;
    }

    @Override
    public InetSocketAddress getServer() {
        return server;
    }

    @Override
    public String getScheme() {
        return scheme;
    }

    @Override
    public HttpMethod getMethod() {
        return method;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public HttpVersion getVersion() {
        return version;
    }

    @Override
    public Map<String, List<String>> getParameters() {
        return parameters;
    }

    @Override
    public String getParameterValue(String name) {
        return Optional.ofNullable(parameters.get(name)).map(list -> list.get(0)).orElse(null);
    }

    @Override
    public List<String> getParameterValues(String name) {
        return parameters.getOrDefault(name, Collections.emptyList());
    }

    @Override
    public HttpHeaders getHttpHeaders() {
        return headers;
    }

    @Override
    public HttpBody getHttpBody() {
        throw new UnsupportedOperationException();
    }

}
