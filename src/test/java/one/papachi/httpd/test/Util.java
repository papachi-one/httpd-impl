package one.papachi.httpd.test;

import one.papachi.httpd.api.http.HttpsTLSSupplier;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class Util {

    public static HttpsTLSSupplier getTLSServer() throws Exception {
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

    public static HttpsTLSSupplier getTLSClient() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(new KeyManager[0], new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }}, null);
        HttpsTLSSupplier tls = () -> {
            SSLEngine sslEngine = sslContext.createSSLEngine();
            sslEngine.setUseClientMode(true);
            sslEngine.setNeedClientAuth(false);
            sslEngine.setWantClientAuth(false);
            SSLParameters sslParameters = sslEngine.getSSLParameters();
            sslParameters.setApplicationProtocols(new String[]{"h2", "http/1.1"});
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
