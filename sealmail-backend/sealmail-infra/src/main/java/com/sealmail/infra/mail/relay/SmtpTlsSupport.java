package com.sealmail.infra.mail.relay;

import com.sealmail.infra.config.properties.StandardTlsProperties;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;

final class SmtpTlsSupport {

    private final StandardTlsProperties properties;
    private final SSLSocketFactory sslSocketFactory;

    SmtpTlsSupport(StandardTlsProperties properties) {
        this.properties = properties != null ? properties : new StandardTlsProperties();
        this.sslSocketFactory = createSslSocketFactory(this.properties);
    }

    Upgrade upgrade(Socket socket, String host, int port) throws java.io.IOException {
        SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(socket, host, port, true);
        sslSocket.setUseClientMode(true);
        sslSocket.setSoTimeout(socket.getSoTimeout());
        configureStandardTlsSocket(sslSocket);
        sslSocket.startHandshake();
        return new Upgrade(sslSocket, tlsInfo(sslSocket.getSession()));
    }

    private void configureStandardTlsSocket(SSLSocket sslSocket) {
        List<String> protocols = properties.getProtocols();
        if (protocols != null && !protocols.isEmpty()) {
            sslSocket.setEnabledProtocols(protocols.toArray(String[]::new));
        }
        List<String> cipherSuites = properties.getCipherSuites();
        if (cipherSuites != null && !cipherSuites.isEmpty()) {
            sslSocket.setEnabledCipherSuites(cipherSuites.toArray(String[]::new));
        }
    }

    private static SSLSocketFactory createSslSocketFactory(StandardTlsProperties properties) {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers(properties), new SecureRandom());
            return context.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize standard TLS context: " + e.getMessage(), e);
        }
    }

    private static TrustManager[] trustManagers(StandardTlsProperties properties) throws Exception {
        if (!properties.isVerifyPeerCertificate() || properties.isTrustAll()) {
            return new TrustManager[]{new TrustAllManager()};
        }
        String trustStorePath = properties.getTrustStorePath();
        if (trustStorePath == null || trustStorePath.isBlank()) {
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            return factory.getTrustManagers();
        }

        KeyStore trustStore = KeyStore.getInstance(properties.getTrustStoreType());
        try (InputStream input = new FileInputStream(trustStorePath)) {
            trustStore.load(input, properties.getTrustStorePassword().toCharArray());
        }
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(trustStore);
        return factory.getTrustManagers();
    }

    private static SmtpTlsInfo tlsInfo(SSLSession session) {
        return new SmtpTlsInfo(
                session.getProtocol(),
                session.getCipherSuite(),
                peerFingerprint(session)
        );
    }

    private static String peerFingerprint(SSLSession session) {
        try {
            java.security.cert.Certificate[] certificates = session.getPeerCertificates();
            if (certificates.length == 0 || !(certificates[0] instanceof X509Certificate certificate)) {
                return null;
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            return hex(digest);
        } catch (SSLPeerUnverifiedException | CertificateEncodingException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02X", b));
        }
        return builder.toString();
    }

    record Upgrade(SSLSocket socket, SmtpTlsInfo tlsInfo) {
    }

    private static final class TrustAllManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
