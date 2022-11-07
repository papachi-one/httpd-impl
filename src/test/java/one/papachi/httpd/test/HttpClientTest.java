package one.papachi.httpd.test;

import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.api.http.HttpHeader;
import one.papachi.httpd.api.http.HttpRequest;
import one.papachi.httpd.api.http.HttpResponse;
import one.papachi.httpd.impl.http.DefaultHttpBody;
import one.papachi.httpd.impl.http.DefaultHttpRequest;
import one.papachi.httpd.impl.http.client.DefaultHttpClient;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;

public class HttpClientTest {

    public static void main(String[] args) throws Exception {
        DefaultHttpClient client = new DefaultHttpClient();
//        client.setOption(StandardHttpOptions.TLS, Util.getTLSClient());
        while (true) {
            DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
            builder.setMethod("POST");
            builder.addHeader("Host", "local.papachi.one");
            builder.addHeader("Content-Type", "application/json");
            builder.addHeader("Content-Type", "application/octet-stream");
//            builder.setBody(new DefaultHttpBody.DefaultBuilder().setInput("{\"name\":\"Pavel Csefalvay\"}").build());
            builder.setBody(new DefaultHttpBody.DefaultBuilder().setInput(Path.of("c:\\Users\\PC\\Downloads\\fcp2121021.mp4")).build());
            HttpRequest request = builder.build();
            HttpResponse response = client.send("local.papachi.one", 80, false, request).get();
            System.out.println(response);

            System.out.println(response.getStatusLine());
            response.getHeaders().stream().map(HttpHeader::getHeaderLine).forEach(System.out::println);

            HttpBody httpBody = response.getHttpBody();
            if (httpBody.isPresent()) {
                ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
                BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream("client.out"));
                int result;
                while ((result = httpBody.read(buffer.clear()).get()) != -1) {
                    buffer.flip();
                    outputStream.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
//                sb.append(new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining()));
                }
                outputStream.close();
            }

//            String responseBody = Util.readString(response.getHttpBody());
//            System.out.println(responseBody);
//            Thread.sleep(5000);
            break;
        }
    }

    public static void main1(String[] args) throws Exception {
        DefaultHttpClient client = new DefaultHttpClient();
        while (true) {
            DefaultHttpRequest.DefaultBuilder builder = new DefaultHttpRequest.DefaultBuilder();
            builder.addHeader("Host", "ifconfig.io");
            HttpRequest request = builder.build();
            HttpResponse response = client.send("ifconfig.io", 80, false, request).get();
            System.out.println(response);

            System.out.println(response.getStatusLine());
            response.getHeaders().stream().map(HttpHeader::getHeaderLine).forEach(System.out::println);

            String responseBody = Util.readString(response.getHttpBody());
            System.out.println(responseBody.length());
            Thread.sleep(5000);
        }
    }

}
