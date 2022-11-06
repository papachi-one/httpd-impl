package one.papachi.httpd.test;

import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.impl.http.DefaultHttpRequest;
import one.papachi.httpd.impl.http.client.DefaultHttpClient;

public class HttpClientTest {

    public static void main(String[] args) throws Exception {
        String s = "http://ifconfig.io";
        DefaultHttpClient client = new DefaultHttpClient();
        while (true) {
            DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
            builder.addHeader("Host", "ifconfig.io");
            HttpRequest request = builder.build();
            HttpResponse response = client.send(request).get();
            System.out.println(response);

            System.out.println(response.getStatusLine());
            response.getHeaders().stream().map(HttpHeader::getHeaderLine).forEach(System.out::println);

            String responseBody = Util.readString(response.getHttpBody());
            System.out.println(responseBody.length());
            Thread.sleep(5000);
        }
    }

}
