package one.papachi.httpd.impl.spi;

import one.papachi.httpd.api.http.HttpClient;
import one.papachi.httpd.api.spi.HttpClientProvider;
import one.papachi.httpd.impl.http.client.DefaultHttpClient;

public class DefaultHttpClientProvider implements HttpClientProvider {

    @Override
    public HttpClient getHttpClient() {
        return new DefaultHttpClient();
    }

}
