package one.papachi.httpd.test;

import one.papachi.httpd.api.http.HttpsTLSSupplier;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.security.KeyStore;

public class Util {

    public static HttpsTLSSupplier getTLS() throws Exception {
        String keyStoreFile = "keystore.p12";
        String trustStoreFile = "keystore.p12";
        KeyStore ks = KeyStore.getInstance("PKCS12");
        KeyStore ts = KeyStore.getInstance("PKCS12");
        char[] passphrase = "heslo".toCharArray();
        ks.load(new FileInputStream(keyStoreFile), passphrase);
        ts.load(new FileInputStream(trustStoreFile), passphrase);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);
        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        HttpsTLSSupplier tls = () -> {
            SSLEngine sslEngine = sslContext.createSSLEngine();
            sslEngine.setUseClientMode(false);
            sslEngine.setNeedClientAuth(false);
            sslEngine.setWantClientAuth(false);
            SSLParameters sslParameters = sslEngine.getSSLParameters();
            sslParameters.setApplicationProtocols(new String[]{"h2", "http/1.1"});
//            sslParameters.setApplicationProtocols(new String[]{"http/1.1"});
            sslEngine.setSSLParameters(sslParameters);
            return sslEngine;
        };
        return tls;
    }

    public static String readString(AsynchronousByteChannel channel) {
        if (channel == null) {
            return "NULL";
        }
        StringBuilder sb = new StringBuilder();
        try {
            ByteBuffer buffer = ByteBuffer.allocate(8 * 1024);
            while (channel.read(buffer).get() != -1) {
                buffer.flip();
                String string = new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
                sb.append(string);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

}
