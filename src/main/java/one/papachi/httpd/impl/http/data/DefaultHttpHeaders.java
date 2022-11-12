package one.papachi.httpd.impl.http.data;


import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpHeaders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DefaultHttpHeaders implements HttpHeaders {

    public static class DefaultBuilder implements HttpHeaders.Builder {

        private List<HttpHeader> headers = new ArrayList<>();

        @Override
        public Builder headerLine(String line) {
            header(new DefaultHttpHeader.DefaultBuilder().headerLine(line).build());
            return this;
        }

        public Builder header(HttpHeader header) {
            headers.add(header);
            return this;
        }

        @Override
        public Builder header(String name, String value) {
            header(new DefaultHttpHeader.DefaultBuilder().name(name).value(value).build());
            return this;
        }

        @Override
        public HttpHeaders build() {
            return new DefaultHttpHeaders(headers);
        }

    }

    private final Map<String, List<HttpHeader>> headers = new LinkedHashMap<>();

    DefaultHttpHeaders(List<HttpHeader> headers) {
        headers.forEach(header -> this.headers.compute(header.getName().toLowerCase(), (key, value) -> {
            value = value != null ? value : new LinkedList<>();
            value.add(header);
            return value;
        }));
    }

    @Override
    public List<HttpHeader> getHeaders() {
        return headers.values().stream().flatMap(Collection::stream).toList();
    }

    @Override
    public HttpHeader getHeader(String name) {
        return Optional.ofNullable(headers.get(name.toLowerCase())).map(list -> list.get(0)).orElse(null);
    }

    @Override
    public List<HttpHeader> getHeaders(String name) {
        return headers.get(name.toLowerCase());
    }

    @Override
    public String getHeaderValue(String name) {
        return Optional.ofNullable(headers.get(name.toLowerCase())).map(list -> list.get(0)).map(HttpHeader::getValue).orElse(null);
    }

    @Override
    public List<String> getHeaderValues(String name) {
        return headers.getOrDefault(name.toLowerCase(), Collections.emptyList()).stream().map(HttpHeader::getValue).toList();
    }

}
