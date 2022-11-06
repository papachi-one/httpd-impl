package one.papachi.httpd.impl.http;



import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpHeaders;
import one.papachi.httpd.api.http.HttpResponse;

import java.io.InputStream;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.ReadableByteChannel;

public class DefaultHttpResponse implements HttpResponse {

    public static class DefaultBuilder implements Builder {

        private String version = "HTTP/1.1";

        private int statusCode = 200;

        private String reasonPhrase = "OK";

        private final HttpHeaders.Builder headersBuilder = new DefaultHttpHeaders.DefaultBuilder();

        private final HttpBody.Builder bodyBuilder = new DefaultHttpBody.DefaultBuilder();

        private HttpBody body;

        @Override
        public Builder setVersion(String version) {
            this.version = version;
            return this;
        }

        @Override
        public Builder setStatusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        @Override
        public Builder setReasonPhrase(String reasonPhrase) {
            this.reasonPhrase = reasonPhrase;
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
        public Builder setBody(AsynchronousByteChannel channel) {
            bodyBuilder.setInput(channel);
            return this;
        }

        @Override
        public Builder setBody(AsynchronousFileChannel channel) {
            bodyBuilder.setInput(channel);
            return this;
        }

        @Override
        public Builder setBody(ReadableByteChannel channel) {
            bodyBuilder.setInput(channel);
            return this;
        }

        @Override
        public Builder setBody(InputStream inputStream) {
            bodyBuilder.setInput(inputStream);
            return this;
        }

        @Override
        public HttpResponse build() {
            return new DefaultHttpResponse(version, statusCode, reasonPhrase, headersBuilder.build(), body != null ? body : bodyBuilder.build());
        }

    }

    private final String version;

    private final int statusCode;

    private final String reasonPhrase;

    private final HttpHeaders headers;

    private final HttpBody body;

    DefaultHttpResponse(String version, int statusCode, String reasonPhrase, HttpHeaders headers, HttpBody body) {
        this.version = version;
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
        this.headers = headers;
        this.body = body;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public int getStatusCode() {
        return statusCode;
    }

    @Override
    public String getReasonPhrase() {
        return reasonPhrase;
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
