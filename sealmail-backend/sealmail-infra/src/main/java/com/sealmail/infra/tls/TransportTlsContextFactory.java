package com.sealmail.infra.tls;

import com.sealmail.infra.config.properties.TransportTlsProperties;
import com.tencent.kona.KonaProvider;
import com.tencent.kona.crypto.KonaCryptoProvider;
import com.tencent.kona.pkix.KonaPKIXProvider;
import com.tencent.kona.ssl.KonaSSLProvider;
import org.springframework.stereotype.Component;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.util.List;

@Component
public class TransportTlsContextFactory {

    private static final String KONA_PROVIDER = "Kona";
    private static final String KONA_SSL_PROVIDER = "KonaSSL";
    private static final String KONA_PKIX_PROVIDER = "KonaPKIX";
    private static final String KONA_CRYPTO_PROVIDER = "KonaCrypto";

    private final TransportTlsProperties properties;
    private volatile SSLContext clientContext;

    public TransportTlsContextFactory(TransportTlsProperties properties) {
        this.properties = properties;
    }

    public SSLContext createServerContext(KeyManager[] keyManagers) throws GeneralSecurityException {
        return createContext(keyManagers);
    }

    public SSLSocket createClientSocket() throws IOException, GeneralSecurityException {
        SSLSocket socket = (SSLSocket) clientContext().getSocketFactory().createSocket();
        socket.setUseClientMode(true);
        configureSocket(socket);
        return socket;
    }

    public SSLSocket wrapClientSocket(Socket socket, String host, int port, int timeoutMillis)
            throws IOException, GeneralSecurityException {
        SSLSocket sslSocket = (SSLSocket) clientContext()
                .getSocketFactory()
                .createSocket(socket, host, port, true);
        sslSocket.setUseClientMode(true);
        sslSocket.setSoTimeout(timeoutMillis);
        configureSocket(sslSocket);
        return sslSocket;
    }

    public SSLSocket wrapServerSocket(Socket socket, SSLContext serverContext) throws IOException {
        SSLSocketFactory socketFactory = serverContext.getSocketFactory();
        InetSocketAddress remoteAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
        SSLSocket sslSocket = (SSLSocket) socketFactory.createSocket(
                socket,
                remoteAddress.getHostString(),
                socket.getPort(),
                true);
        sslSocket.setUseClientMode(false);
        configureSocket(sslSocket);
        return sslSocket;
    }

    public KeyStore createKeyStore() throws GeneralSecurityException {
        registerKonaProvidersIfNeeded();
        if (properties.usesKona() && hasText(properties.getKeyStoreProvider())) {
            return KeyStore.getInstance("PKCS12", properties.getKeyStoreProvider().trim());
        }
        if (properties.usesKona() && KONA_PROVIDER.equals(effectiveProvider())) {
            return KeyStore.getInstance("PKCS12", KONA_PROVIDER);
        }
        return KeyStore.getInstance("PKCS12");
    }

    public KeyManagerFactory createKeyManagerFactory() throws GeneralSecurityException {
        registerKonaProvidersIfNeeded();
        String algorithm = effectiveKeyManagerAlgorithm();
        if (properties.usesKona()) {
            return KeyManagerFactory.getInstance(algorithm, effectiveProvider());
        }
        return KeyManagerFactory.getInstance(algorithm);
    }

    public TransportTlsProperties.Engine engine() {
        return properties.getEngine();
    }

    public String effectiveProvider() {
        if (!properties.usesKona()) {
            return "JDK";
        }
        return hasText(properties.getProvider()) ? properties.getProvider().trim() : KONA_PROVIDER;
    }

    public String effectiveProtocol() {
        if (hasText(properties.getProtocol())) {
            return properties.getProtocol().trim();
        }
        return properties.usesTlcp() ? "TLCP" : "TLS";
    }

    public List<String> effectiveEnabledProtocols() {
        List<String> configured = nonBlank(properties.getEnabledProtocols());
        if (!configured.isEmpty()) {
            return configured;
        }
        if (properties.usesTlcp()) {
            return List.of("TLCPv1.1");
        }
        if (properties.getEngine() == TransportTlsProperties.Engine.KONA_RFC8998_TLS) {
            return List.of("TLSv1.3");
        }
        return List.of();
    }

    public List<String> effectiveEnabledCipherSuites() {
        List<String> configured = nonBlank(properties.getEnabledCipherSuites());
        if (!configured.isEmpty()) {
            return configured;
        }
        if (properties.usesTlcp()) {
            return List.of("TLCP_ECC_SM4_GCM_SM3", "TLCP_ECDHE_SM4_GCM_SM3");
        }
        if (properties.getEngine() == TransportTlsProperties.Engine.KONA_RFC8998_TLS) {
            return List.of("TLS_SM4_GCM_SM3");
        }
        return List.of();
    }

    private SSLContext clientContext() throws GeneralSecurityException {
        SSLContext context = clientContext;
        if (context != null) {
            return context;
        }
        synchronized (this) {
            if (clientContext == null) {
                clientContext = createContext(null);
            }
            return clientContext;
        }
    }

    private SSLContext createContext(KeyManager[] keyManagers) throws GeneralSecurityException {
        registerKonaProvidersIfNeeded();
        configureKonaSystemPropertiesIfNeeded();

        SSLContext context = properties.usesKona()
                ? SSLContext.getInstance(effectiveProtocol(), effectiveProvider())
                : SSLContext.getInstance(effectiveProtocol());
        context.init(keyManagers, null, new SecureRandom());
        return context;
    }

    private String effectiveKeyManagerAlgorithm() {
        if (hasText(properties.getKeyManagerAlgorithm())) {
            return properties.getKeyManagerAlgorithm().trim();
        }
        return properties.usesKona() ? "NewSunX509" : KeyManagerFactory.getDefaultAlgorithm();
    }

    private void configureSocket(SSLSocket socket) {
        List<String> enabledProtocols = effectiveEnabledProtocols();
        if (!enabledProtocols.isEmpty()) {
            socket.setEnabledProtocols(enabledProtocols.toArray(String[]::new));
        }
        List<String> enabledCipherSuites = effectiveEnabledCipherSuites();
        if (!enabledCipherSuites.isEmpty()) {
            socket.setEnabledCipherSuites(enabledCipherSuites.toArray(String[]::new));
        }
    }

    private void registerKonaProvidersIfNeeded() {
        if (!properties.usesKona()) {
            return;
        }
        if (KONA_PROVIDER.equals(effectiveProvider())) {
            if (Security.getProvider(KONA_PROVIDER) == null) {
                Security.addProvider(KonaProvider.instance());
            }
            return;
        }
        if (Security.getProvider(KONA_CRYPTO_PROVIDER) == null) {
            Security.addProvider(KonaCryptoProvider.instance());
        }
        if (Security.getProvider(KONA_PKIX_PROVIDER) == null) {
            Security.addProvider(KonaPKIXProvider.instance());
        }
        if (Security.getProvider(KONA_SSL_PROVIDER) == null) {
            Security.addProvider(KonaSSLProvider.instance());
        }
    }

    private void configureKonaSystemPropertiesIfNeeded() {
        if (!properties.usesKona()) {
            return;
        }
        setSystemPropertyIfPresent("com.tencent.kona.ssl.namedGroups", effectiveNamedGroups());
        setSystemPropertyIfPresent("com.tencent.kona.ssl.client.signatureSchemes", effectiveClientSignatureSchemes());
        setSystemPropertyIfPresent("com.tencent.kona.ssl.server.signatureSchemes", effectiveServerSignatureSchemes());
    }

    private String effectiveNamedGroups() {
        if (hasText(properties.getNamedGroups())) {
            return properties.getNamedGroups().trim();
        }
        if (properties.getEngine() == TransportTlsProperties.Engine.KONA_RFC8998_TLS) {
            return "curveSM2";
        }
        return null;
    }

    private String effectiveClientSignatureSchemes() {
        if (hasText(properties.getClientSignatureSchemes())) {
            return properties.getClientSignatureSchemes().trim();
        }
        if (properties.getEngine() == TransportTlsProperties.Engine.KONA_RFC8998_TLS) {
            return "sm2sig_sm3";
        }
        return null;
    }

    private String effectiveServerSignatureSchemes() {
        if (hasText(properties.getServerSignatureSchemes())) {
            return properties.getServerSignatureSchemes().trim();
        }
        if (properties.getEngine() == TransportTlsProperties.Engine.KONA_RFC8998_TLS) {
            return "sm2sig_sm3";
        }
        return null;
    }

    private void setSystemPropertyIfPresent(String key, String value) {
        if (hasText(value)) {
            System.setProperty(key, value);
        }
    }

    private List<String> nonBlank(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(this::hasText)
                .map(String::trim)
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
