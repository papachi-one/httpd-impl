package one.papachi.httpd.impl;

import one.papachi.httpd.api.http.HttpsTLSSupplier;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

public class Util {

    public static HttpsTLSSupplier getTLSServer() throws KeyStoreException, IOException, NoSuchAlgorithmException, KeyManagementException, CertificateException, UnrecoverableKeyException {
        String keyStoreFile = "c:\\Users\\PC\\Projects\\papachi-toolkit\\keystore.p12";
        String trustStoreFile = "c:\\Users\\PC\\Projects\\papachi-toolkit\\keystore.p12";
        KeyStore ks = KeyStore.getInstance("PKCS12");
        KeyStore ts = KeyStore.getInstance("PKCS12");
        char[] passphrase = "heslo".toCharArray();
        ks.load(new FileInputStream(keyStoreFile), passphrase);
        ts.load(new FileInputStream(trustStoreFile), passphrase);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        HttpsTLSSupplier tls = () -> {
            SSLEngine sslEngine = sslContext.createSSLEngine();
            sslEngine.setEnabledCipherSuites(new String[]{"TLS_RSA_WITH_AES_128_CBC_SHA"});
            sslEngine.setUseClientMode(false);
            sslEngine.setNeedClientAuth(false);
            sslEngine.setWantClientAuth(false);
            SSLParameters sslParameters = sslEngine.getSSLParameters();
            sslParameters.setApplicationProtocols(new String[]{"h2", "http/1.1"});
            sslEngine.setSSLParameters(sslParameters);
            return sslEngine;
        };
        return tls;
    }

    public static HttpsTLSSupplier getTLSClientHttp2And1() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
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
            sslEngine.setEnabledCipherSuites(new String[]{"TLS_RSA_WITH_AES_128_CBC_SHA"});
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

    public static HttpsTLSSupplier getTLSClientHttp2() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
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
            sslEngine.setEnabledCipherSuites(new String[]{"TLS_RSA_WITH_AES_128_CBC_SHA"});
            sslEngine.setUseClientMode(true);
            sslEngine.setNeedClientAuth(false);
            sslEngine.setWantClientAuth(false);
            SSLParameters sslParameters = sslEngine.getSSLParameters();
            sslParameters.setApplicationProtocols(new String[]{"h2"});
            sslEngine.setSSLParameters(sslParameters);
            return sslEngine;
        };
        return tls;
    }

    public static HttpsTLSSupplier getTLSClientHttp1() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
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
            sslEngine.setEnabledCipherSuites(new String[]{"TLS_RSA_WITH_AES_128_CBC_SHA"});
            sslEngine.setUseClientMode(true);
            sslEngine.setNeedClientAuth(false);
            sslEngine.setWantClientAuth(false);
            SSLParameters sslParameters = sslEngine.getSSLParameters();
            sslParameters.setApplicationProtocols(new String[]{"http/1.1"});
            sslEngine.setSSLParameters(sslParameters);
            return sslEngine;
        };
        return tls;
    }

    public static String digest(AsynchronousByteChannel channel) throws NoSuchAlgorithmException {
        if (channel == null) {
            return null;
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try {
            ByteBuffer buffer = ByteBuffer.allocate(8 * 1024);
            while ((channel.read(buffer.clear()).get()) != -1) {
                buffer.flip();
                digest.update(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Base64.getEncoder().encodeToString(digest.digest());
    }

    public static byte[] readBytes(AsynchronousByteChannel channel) {
        if (channel == null) {
            return null;
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            ByteBuffer buffer = ByteBuffer.allocate(8 * 1024);
            while ((channel.read(buffer.clear()).get()) != -1) {
                buffer.flip();
                os.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return os.toByteArray();
    }

    public static String readString(AsynchronousByteChannel channel) {
        if (channel == null) {
            return "NULL";
        }
        StringBuilder sb = new StringBuilder();
        try {
            ByteBuffer buffer = ByteBuffer.allocate(8 * 1024);
            while ((channel.read(buffer.clear()).get()) != -1) {
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
