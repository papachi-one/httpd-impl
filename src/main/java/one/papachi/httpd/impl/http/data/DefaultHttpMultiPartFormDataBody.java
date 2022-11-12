package one.papachi.httpd.impl.http.data;//package one.papachi.http.impl;
//
//import one.papachi.http.api.http.HttpMultiPartFormDataBody;
//import one.papachi.http.api.http.HttpPart;
//
//import java.util.ArrayList;
//import java.util.List;
//
//public class DefaultHttpMultiPartFormDataBody implements HttpMultiPartFormDataBody {
//
//    public static class DefaultBuilder implements Builder {
//
//        private List<HttpPart> parts = new ArrayList<>();
//
//        @Override
//        public Builder addPart(HttpPart part) {
//            parts.add(part);
//            return this;
//        }
//
//        @Override
//        public HttpMultiPartFormDataBody build() {
//            return new DefaultHttpMultiPartFormDataBody(parts);
//        }
//
//    }
//
//    private final List<HttpPart> parts;
//
//    DefaultHttpMultiPartFormDataBody(List<HttpPart> parts) {
//        this.parts = parts;
//    }
//
//    @Override
//    public List<HttpPart> getParts() {
//        return parts;
//    }
//
//}
