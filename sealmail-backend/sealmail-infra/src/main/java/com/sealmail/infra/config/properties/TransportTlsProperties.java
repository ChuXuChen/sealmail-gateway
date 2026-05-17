package com.sealmail.infra.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "sealmail.transport-tls")
public class TransportTlsProperties {

    private Engine engine = Engine.JDK;
    private String provider = "Kona";
    private String protocol;
    private List<String> enabledProtocols = new ArrayList<>();
    private List<String> enabledCipherSuites = new ArrayList<>();
    private String keyManagerAlgorithm;
    private String keyStoreProvider;
    private String namedGroups;
    private String clientSignatureSchemes;
    private String serverSignatureSchemes;

    public Engine getEngine() {
        return engine;
    }

    public void setEngine(Engine engine) {
        this.engine = engine;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public List<String> getEnabledProtocols() {
        return enabledProtocols;
    }

    public void setEnabledProtocols(List<String> enabledProtocols) {
        this.enabledProtocols = enabledProtocols != null ? enabledProtocols : new ArrayList<>();
    }

    public List<String> getEnabledCipherSuites() {
        return enabledCipherSuites;
    }

    public void setEnabledCipherSuites(List<String> enabledCipherSuites) {
        this.enabledCipherSuites = enabledCipherSuites != null ? enabledCipherSuites : new ArrayList<>();
    }

    public String getKeyManagerAlgorithm() {
        return keyManagerAlgorithm;
    }

    public void setKeyManagerAlgorithm(String keyManagerAlgorithm) {
        this.keyManagerAlgorithm = keyManagerAlgorithm;
    }

    public String getKeyStoreProvider() {
        return keyStoreProvider;
    }

    public void setKeyStoreProvider(String keyStoreProvider) {
        this.keyStoreProvider = keyStoreProvider;
    }

    public String getNamedGroups() {
        return namedGroups;
    }

    public void setNamedGroups(String namedGroups) {
        this.namedGroups = namedGroups;
    }

    public String getClientSignatureSchemes() {
        return clientSignatureSchemes;
    }

    public void setClientSignatureSchemes(String clientSignatureSchemes) {
        this.clientSignatureSchemes = clientSignatureSchemes;
    }

    public String getServerSignatureSchemes() {
        return serverSignatureSchemes;
    }

    public void setServerSignatureSchemes(String serverSignatureSchemes) {
        this.serverSignatureSchemes = serverSignatureSchemes;
    }

    public boolean usesKona() {
        return engine == Engine.KONA_RFC8998_TLS || engine == Engine.KONA_TLCP;
    }

    public boolean usesTlcp() {
        return engine == Engine.KONA_TLCP;
    }

    public enum Engine {
        JDK,
        KONA_RFC8998_TLS,
        KONA_TLCP
    }
}
