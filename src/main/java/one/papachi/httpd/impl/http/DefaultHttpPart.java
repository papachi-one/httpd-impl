//package one.papachi.httpd.impl.http;
//
//
//import one.papachi.httpd.api.http.HttpBody;
//import one.papachi.httpd.api.http.HttpHeader;
//import one.papachi.httpd.api.http.HttpHeaders;
//import one.papachi.httpd.api.http.HttpPart;
//
//public class DefaultHttpPart implements HttpPart {
//
//    public static class DefaultBuilder implements Builder {
//
//        private final HttpHeaders.Builder headersBuilder = new DefaultHttpHeaders.DefaultBuilder();
//
//        private HttpBody body;
//
//        @Override
//        public Builder setName(String name) {
//            headersBuilder.addHeader("Content-Disposition", "form-data; name=\"" + name + "\"");
//            return this;
//        }
//
//        @Override
//        public Builder addHeaderLine(String line) {
//            headersBuilder.addHeaderLine(line);
//            return this;
//        }
//
//        @Override
//        public Builder addHeader(HttpHeader header) {
//            headersBuilder.addHeader(header);
//            return this;
//        }
//
//        @Override
//        public Builder addHeader(String name, String value) {
//            headersBuilder.addHeader(name, value);
//            return this;
//        }
//
//        @Override
//        public Builder setBody(HttpBody body) {
//            this.body = body;
//            return this;
//        }
//
//        @Override
//        public HttpPart build() {
//            return new DefaultHttpPart(headersBuilder.build(), body);
//        }
//    }
//
//    private final HttpHeaders headers;
//
//    private final HttpBody body;
//
//    DefaultHttpPart(HttpHeaders headers, HttpBody body) {
//        this.headers = headers;
//        this.body = body;
//    }
//
//    @Override
//    public HttpHeaders getHttpHeaders() {
//        return null;
//    }
//
//    @Override
//    public HttpBody getHttpBody() {
//        return null;
//    }
//
//}
