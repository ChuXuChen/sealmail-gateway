package com.sealmail.infra.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sealmail.smtp.server")
public class SmtpServerProperties {

    private int port = 2525;
    private String bindAddress = "0.0.0.0";
    private int maxConnections = 100;
    private int maxMessageSize = 10485760;
    private boolean enableStartTls = false;
    private boolean requireTls = false;
    private String keystorePath;
    private String keystorePasswordSecretRef;
    private String keyAlias;
    private String keyPasswordSecretRef;
    private String certificatePath;
    private String privateKeyPath;
    private String privateKeyPasswordSecretRef;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public void setBindAddress(String bindAddress) {
        this.bindAddress = bindAddress;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    public void setMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    public boolean isEnableStartTls() {
        return enableStartTls;
    }

    public void setEnableStartTls(boolean enableStartTls) {
        this.enableStartTls = enableStartTls;
    }

    public boolean isRequireTls() {
        return requireTls;
    }

    public void setRequireTls(boolean requireTls) {
        this.requireTls = requireTls;
    }

    public String getKeystorePath() {
        return keystorePath;
    }

    public void setKeystorePath(String keystorePath) {
        this.keystorePath = keystorePath;
    }

    public String getKeystorePasswordSecretRef() {
        return keystorePasswordSecretRef;
    }

    public void setKeystorePasswordSecretRef(String keystorePasswordSecretRef) {
        this.keystorePasswordSecretRef = keystorePasswordSecretRef;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public void setKeyAlias(String keyAlias) {
        this.keyAlias = keyAlias;
    }

    public String getKeyPasswordSecretRef() {
        return keyPasswordSecretRef;
    }

    public void setKeyPasswordSecretRef(String keyPasswordSecretRef) {
        this.keyPasswordSecretRef = keyPasswordSecretRef;
    }

    public String getCertificatePath() {
        return certificatePath;
    }

    public void setCertificatePath(String certificatePath) {
        this.certificatePath = certificatePath;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getPrivateKeyPasswordSecretRef() {
        return privateKeyPasswordSecretRef;
    }

    public void setPrivateKeyPasswordSecretRef(String privateKeyPasswordSecretRef) {
        this.privateKeyPasswordSecretRef = privateKeyPasswordSecretRef;
    }
}
