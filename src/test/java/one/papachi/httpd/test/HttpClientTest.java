package one.papachi.httpd.test;

import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpMethod;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.impl.Util;
import one.papachi.httpd.impl.http.DefaultHttpBody;
import one.papachi.httpd.impl.http.DefaultHttpRequest;
import one.papachi.httpd.impl.http.client.DefaultHttpClient;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Path;

public class HttpClientTest {

    public static void main(String[] args) throws Exception {
        DefaultHttpClient client = new DefaultHttpClient();
//        client.setOption(StandardHttpOptions.TLS, one.papachi.httpd.impl.Util.getTLSClient());
        while (true) {
            DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
            builder.method(HttpMethod.POST);
            builder.header("Host", "local.papachi.one");
            builder.header("Content-Type", "application/octet-stream");
            builder.header("Content-Length", "498251");
            builder.body(new DefaultHttpBody.DefaultBuilder().input(Path.of("c:\\Users\\PC\\Downloads\\15W vs 25W.png")).build());
            HttpRequest request = builder.build();
            HttpResponse response = client.send(new URL("http://local.papachi.one"), request).get();
            System.out.println(response);

            System.out.println(response.getStatusCode());
            response.getHeaders().stream().map(HttpHeader::getHeaderLine).forEach(System.out::println);

            HttpBody httpBody = response.getHttpBody();
            if (httpBody.isPresent()) {
                ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
                BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream("data.client"));
                while ((httpBody.read(buffer.clear()).get()) != -1) {
                    buffer.flip();
                    outputStream.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
                }
                outputStream.close();
            }
            break;
        }
    }

    public static void main1(String[] args) throws Exception {
        DefaultHttpClient client = new DefaultHttpClient();
        while (true) {
            DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
            builder.header("Host", "ifconfig.io");
            HttpRequest request = builder.build();
            HttpResponse response = client.send(new URL("http://ifconfig.io"), request).get();

            System.out.println(response.getStatusCode());
            response.getHeaders().stream().map(HttpHeader::getHeaderLine).forEach(System.out::println);

            String responseBody = Util.readString(response.getHttpBody());
            System.out.println(responseBody.length());
            Thread.sleep(5000);
        }
    }

}
