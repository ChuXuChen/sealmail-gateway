package com.sealmail.edge.tls;

import com.sealmail.edge.config.EdgeConfig;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public final class EdgeTlsContextFactory {
    private final EdgeConfig.Tls tlsConfig;
    private final SSLContext context;

    public EdgeTlsContextFactory(EdgeConfig.Tls tlsConfig) {
        this.tlsConfig = tlsConfig;
        KonaSecurity.registerProviders();
        this.context = createContext(tlsConfig);
    }

    public javax.net.ssl.SSLSocket wrapClient(Socket socket, String host, int port) throws IOException {
        javax.net.ssl.SSLSocket sslSocket = (javax.net.ssl.SSLSocket) context.getSocketFactory()
                .createSocket(socket, host, port, true);
        applyParameters(sslSocket, true);
        return sslSocket;
    }

    public javax.net.ssl.SSLSocket wrapServer(Socket socket) throws IOException {
        javax.net.ssl.SSLSocket sslSocket = (javax.net.ssl.SSLSocket) context.getSocketFactory()
                .createSocket(socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);
        sslSocket.setUseClientMode(false);
        applyParameters(sslSocket, false);
        return sslSocket;
    }

    public TlsDiagnostics diagnostics() {
        try (javax.net.ssl.SSLSocket socket = (javax.net.ssl.SSLSocket) context.getSocketFactory().createSocket()) {
            applyParameters(socket, true);
            return new TlsDiagnostics(
                    List.of(socket.getEnabledProtocols()),
                    List.of(socket.getEnabledCipherSuites()),
                    List.of(socket.getSupportedProtocols()),
                    List.of(socket.getSupportedCipherSuites()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Kona TLS diagnostics", e);
        }
    }

    private void applyParameters(javax.net.ssl.SSLSocket socket, boolean client) {
        socket.setUseClientMode(client);
        SSLParameters parameters = socket.getSSLParameters();
        List<String> supportedProtocols = List.of(socket.getSupportedProtocols());
        String[] protocols = tlsConfig.protocols().stream()
                .filter(supportedProtocols::contains)
                .toArray(String[]::new);
        if (protocols.length == 0) {
            throw new IllegalStateException("No configured GM TLS protocols are supported by the active provider: "
                    + tlsConfig.protocols());
        }
        parameters.setProtocols(protocols);

        List<String> supportedCipherSuites = List.of(socket.getSupportedCipherSuites());
        String[] cipherSuites = tlsConfig.cipherSuites().stream()
                .filter(supportedCipherSuites::contains)
                .toArray(String[]::new);
        if (!tlsConfig.cipherSuites().isEmpty() && cipherSuites.length == 0) {
            throw new IllegalStateException("No configured GM TLS cipher suites are supported by the active provider: "
                    + tlsConfig.cipherSuites());
        }
        if (cipherSuites.length != 0) {
            parameters.setCipherSuites(cipherSuites);
        }
        socket.setSSLParameters(parameters);
    }

    private static SSLContext createContext(EdgeConfig.Tls tlsConfig) {
        try {
            SSLContext context = sslContext(tlsConfig.protocols());
            context.init(
                    tlsConfig.keyStore() == null ? null : keyManagers(tlsConfig),
                    trustManagers(tlsConfig),
                    SecureRandom.getInstanceStrong()
            );
            return context;
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Failed to initialize Kona TLS context", e);
        }
    }

    private static SSLContext sslContext(List<String> protocols) throws GeneralSecurityException {
        List<String> candidates = new ArrayList<>(protocols);
        candidates.add("TLS");
        GeneralSecurityException last = null;
        for (String protocol : candidates) {
            try {
                return SSLContext.getInstance(protocol);
            } catch (GeneralSecurityException e) {
                last = e;
            }
        }
        throw last == null ? new GeneralSecurityException("No SSLContext protocol configured") : last;
    }

    private static javax.net.ssl.KeyManager[] keyManagers(EdgeConfig.Tls tlsConfig)
            throws GeneralSecurityException, IOException {
        KeyStore keyStore = loadStore(tlsConfig.keyStore(), tlsConfig.keyStoreType(), tlsConfig.keyStorePassword());
        KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore, tlsConfig.keyStorePassword().toCharArray());
        return factory.getKeyManagers();
    }

    private static TrustManager[] trustManagers(EdgeConfig.Tls tlsConfig)
            throws GeneralSecurityException, IOException {
        if (tlsConfig.trustAll()) {
            return new TrustManager[]{new TrustAllManager()};
        }
        if (tlsConfig.trustStore() == null) {
            return null;
        }
        KeyStore trustStore = loadStore(tlsConfig.trustStore(), tlsConfig.trustStoreType(), tlsConfig.trustStorePassword());
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(trustStore);
        return factory.getTrustManagers();
    }

    private static KeyStore loadStore(java.nio.file.Path path, String type, String password)
            throws GeneralSecurityException, IOException {
        KeyStore store = keyStore(type);
        try (InputStream input = Files.newInputStream(path)) {
            store.load(input, password.toCharArray());
        }
        return store;
    }

    private static KeyStore keyStore(String type) throws GeneralSecurityException {
        String normalizedType = type == null || type.isBlank() ? "PKCS12" : type;
        for (String providerName : List.of("Kona", "KonaPKIX")) {
            Provider provider = Security.getProvider(providerName);
            if (provider != null && provider.getService("KeyStore", normalizedType) != null) {
                return KeyStore.getInstance(normalizedType, provider);
            }
        }
        return KeyStore.getInstance(normalizedType);
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

    public record TlsDiagnostics(
            List<String> enabledProtocols,
            List<String> enabledCipherSuites,
            List<String> supportedProtocols,
            List<String> supportedCipherSuites
    ) {
        public TlsDiagnostics {
            enabledProtocols = List.copyOf(enabledProtocols == null ? List.of() : enabledProtocols);
            enabledCipherSuites = List.copyOf(enabledCipherSuites == null ? List.of() : enabledCipherSuites);
            supportedProtocols = List.copyOf(supportedProtocols == null ? List.of() : supportedProtocols);
            supportedCipherSuites = List.copyOf(supportedCipherSuites == null ? List.of() : supportedCipherSuites);
        }
    }
}
