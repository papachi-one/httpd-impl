package one.papachi.httpd.impl.http;//package one.papachi.http.impl;
//
//
//import one.papachi.http.api.http.HttpXWwwFormUrlEncodedBody;
//
//import java.util.Collections;
//import java.util.LinkedHashMap;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//
//public class DefaultHttpXWwwFormUrlEncodedBody implements HttpXWwwFormUrlEncodedBody {
//
//    public static class DefaultBuilder implements Builder {
//
//        private final Map<String, List<String>> parameters = new LinkedHashMap<>();
//
//        private String name;
//
//        private String value;
//
//        public Builder addString(String string, byte b) {
//            if (b == '=') {
//                name = string;
//            } else if (b == '&') {
//                if (name == null) {
//                    name = string;
//                } else {
//                    value = string;
//                }
//                addParameter(name, value);
//                name = value = null;
//            }
//            return this;
//        }
//
//        public Builder addString(String string) {
//            if (name == null) {
//                name = string;
//            } else {
//                value = string;
//            }
//            return this;
//        }
//
//        @Override
//        public Builder addParameter(String name, String value) {
//            parameters.compute(name, (k, v) -> {
//                v = v != null ? v : new LinkedList<>();
//                v.add(value);
//                return v;
//            });
//            return this;
//        }
//
//        @Override
//        public HttpXWwwFormUrlEncodedBody build() {
//            if (name != null) {
//                addParameter(name, value);
//            }
//            return new DefaultHttpXWwwFormUrlEncodedBody(parameters);
//        }
//
//    }
//
//    private final Map<String, List<String>> parameters;
//
//    DefaultHttpXWwwFormUrlEncodedBody(Map<String, List<String>> parameters) {
//        this.parameters = parameters;
//    }
//
//    @Override
//    public Map<String, List<String>> getParameters() {
//        return parameters;
//    }
//
//    @Override
//    public String getParameterValue(String name) {
//        return Optional.ofNullable(parameters.get(name)).map(list -> list.get(0)).orElse(null);
//    }
//
//    @Override
//    public List<String> getParameterValues(String name) {
//        return parameters.getOrDefault(name, Collections.emptyList());
//    }
//
//}
