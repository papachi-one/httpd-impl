package one.papachi.httpd.impl.http;


import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpHeaders;
import one.papachi.httpd.api.http.HttpMethod;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpVersion;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DefaultHttpRequest implements HttpRequest {

    public static class DefaultBuilder implements HttpRequest.Builder {

        private HttpMethod method = HttpMethod.GET;

        private String path = "/";

        private HttpVersion version = HttpVersion.HTTP_1_1;

        private final Map<String, List<String>> parameters = new LinkedHashMap<>();

        private final HttpHeaders.Builder headersBuilder = new DefaultHttpHeaders.DefaultBuilder();

        private HttpBody body;

        @Override
        public Builder setMethod(HttpMethod method) {
            this.method = method;
            return this;
        }

        @Override
        public Builder setPath(String path) {
            parameters.clear();
            String[] split1 = path.split("\\?", 2);
            String[] split2 = split1.length == 2 ? split1[1].split("&") : new String[0];
            for (String string : split2) {
                String[] split3 = string.split("=", 2);
                String name = URLDecoder.decode(split3[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(split3.length == 2 ? split3[1] : "", StandardCharsets.UTF_8);
                addParameter(name, value);
            }
            this.path = split1[0];
            return this;
        }

        @Override
        public Builder setVersion(HttpVersion version) {
            this.version = version;
            return this;
        }

        @Override
        public Builder addParameter(String name, String value) {
            parameters.compute(name, (k, v) -> {
                v = v != null ? v : new LinkedList<>();
                v.add(value);
                return v;
            });
            return this;
        }

        @Override
        public Builder addHeaderLine(String line) {
            headersBuilder.addHeaderLine(line);
            return this;
        }

        @Override
        public Builder addHeader(HttpHeader header) {
            headersBuilder.addHeader(header);
            return this;
        }

        @Override
        public Builder addHeader(String name, String value) {
            headersBuilder.addHeader(name, value);
            return this;
        }

        @Override
        public Builder setBody(HttpBody body) {
            this.body = body;
            return this;
        }

        @Override
        public HttpRequest build() {
            return new DefaultHttpRequest(method, path, version, parameters, headersBuilder.build(), body);
        }

    }

    private final HttpMethod method;

    private final String path;

    private final HttpVersion version;

    private final Map<String, List<String>> parameters;

    private final HttpHeaders headers;

    private final HttpBody body;

    DefaultHttpRequest(HttpMethod method, String path, HttpVersion version, Map<String, List<String>> parameters, HttpHeaders headers, HttpBody body) {
        this.method = method;
        this.path = path;
        this.version = version;
        this.parameters = parameters;
        this.headers = headers;
        this.body = body;
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
        return body;
    }

}
