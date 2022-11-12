package one.papachi.httpd.impl.http.data;


import one.papachi.httpd.api.http.HttpHeader;

public class DefaultHttpHeader implements HttpHeader {

    public static class DefaultBuilder implements HttpHeader.Builder {

        private String name, value;

        @Override
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public Builder value(String value) {
            this.value = value;
            return this;
        }

        @Override
        public HttpHeader build() {
            return new DefaultHttpHeader(name, value);
        }

    }

    private final String name;

    private final String value;

    DefaultHttpHeader(String name, String value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public String getHeaderLine() {
        return name + ": " + value;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return getHeaderLine();
    }
}
