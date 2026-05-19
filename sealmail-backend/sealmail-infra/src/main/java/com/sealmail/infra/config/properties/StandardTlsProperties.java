package com.sealmail.infra.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "sealmail.standard-tls")
public class StandardTlsProperties {

    private List<String> protocols = new ArrayList<>(List.of("TLSv1.3", "TLSv1.2"));
    private List<String> cipherSuites = new ArrayList<>();
    private boolean verifyPeerCertificate = true;
    private boolean trustAll = false;
    private String trustStorePath;
    private String trustStorePassword = "";
    private String trustStoreType = "PKCS12";

    public List<String> getProtocols() {
        return protocols;
    }

    public void setProtocols(List<String> protocols) {
        this.protocols = protocols != null ? new ArrayList<>(protocols) : new ArrayList<>();
    }

    public List<String> getCipherSuites() {
        return cipherSuites;
    }

    public void setCipherSuites(List<String> cipherSuites) {
        this.cipherSuites = cipherSuites != null ? new ArrayList<>(cipherSuites) : new ArrayList<>();
    }

    public boolean isVerifyPeerCertificate() {
        return verifyPeerCertificate;
    }

    public void setVerifyPeerCertificate(boolean verifyPeerCertificate) {
        this.verifyPeerCertificate = verifyPeerCertificate;
    }

    public boolean isTrustAll() {
        return trustAll;
    }

    public void setTrustAll(boolean trustAll) {
        this.trustAll = trustAll;
    }

    public String getTrustStorePath() {
        return trustStorePath;
    }

    public void setTrustStorePath(String trustStorePath) {
        this.trustStorePath = trustStorePath;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword != null ? trustStorePassword : "";
    }

    public String getTrustStoreType() {
        return trustStoreType;
    }

    public void setTrustStoreType(String trustStoreType) {
        this.trustStoreType = trustStoreType != null && !trustStoreType.isBlank() ? trustStoreType : "PKCS12";
    }
}
