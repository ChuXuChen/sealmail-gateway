package com.sealmail.infra.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "sealmail.mail-auth")
public class MailAuthProperties {

    private boolean enabled = true;
    private String authservId = "sealmail-gateway";
    private boolean skipPrivateRelay = true;
    private final TrustedSource trustedSource = new TrustedSource();
    private final Dkim dkim = new Dkim();
    private final Spf spf = new Spf();
    private final Dmarc dmarc = new Dmarc();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAuthservId() {
        return authservId;
    }

    public void setAuthservId(String authservId) {
        this.authservId = authservId;
    }

    public boolean isSkipPrivateRelay() {
        return skipPrivateRelay;
    }

    public void setSkipPrivateRelay(boolean skipPrivateRelay) {
        this.skipPrivateRelay = skipPrivateRelay;
    }

    public Dkim getDkim() {
        return dkim;
    }

    public TrustedSource getTrustedSource() {
        return trustedSource;
    }

    public Spf getSpf() {
        return spf;
    }

    public Dmarc getDmarc() {
        return dmarc;
    }

    public static class Dkim {
        private boolean enabled = true;
        private String selector = "sealmail";
        private String privateKeyPath;
        private String privateKeySecretRef;
        private List<String> signedHeaders = new ArrayList<>(
                List.of("from", "to", "subject", "date", "message-id")
        );

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSelector() {
            return selector;
        }

        public void setSelector(String selector) {
            this.selector = selector;
        }

        public String getPrivateKeyPath() {
            return privateKeyPath;
        }

        public void setPrivateKeyPath(String privateKeyPath) {
            this.privateKeyPath = privateKeyPath;
        }

        public String getPrivateKeySecretRef() {
            return privateKeySecretRef;
        }

        public void setPrivateKeySecretRef(String privateKeySecretRef) {
            this.privateKeySecretRef = privateKeySecretRef;
        }

        public List<String> getSignedHeaders() {
            return signedHeaders;
        }

        public void setSignedHeaders(List<String> signedHeaders) {
            this.signedHeaders = signedHeaders != null ? signedHeaders : new ArrayList<>();
        }
    }

    public static class TrustedSource {
        private List<String> trustedRelayCidrs = new ArrayList<>(List.of("127.0.0.1/32", "::1/128"));
        private List<String> originalIpHeaders = new ArrayList<>(List.of("X-Original-Client-IP", "X-Forwarded-For"));

        public List<String> getTrustedRelayCidrs() {
            return trustedRelayCidrs;
        }

        public void setTrustedRelayCidrs(List<String> trustedRelayCidrs) {
            this.trustedRelayCidrs = trustedRelayCidrs != null ? trustedRelayCidrs : new ArrayList<>();
        }

        public List<String> getOriginalIpHeaders() {
            return originalIpHeaders;
        }

        public void setOriginalIpHeaders(List<String> originalIpHeaders) {
            this.originalIpHeaders = originalIpHeaders != null ? originalIpHeaders : new ArrayList<>();
        }
    }

    public static class Spf {
        private boolean enabled = true;
        private int maxDnsLookups = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxDnsLookups() {
            return maxDnsLookups;
        }

        public void setMaxDnsLookups(int maxDnsLookups) {
            this.maxDnsLookups = maxDnsLookups;
        }
    }

    public static class Dmarc {
        private boolean enabled = true;
        private boolean quarantineRejectPolicy = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isQuarantineRejectPolicy() {
            return quarantineRejectPolicy;
        }

        public void setQuarantineRejectPolicy(boolean quarantineRejectPolicy) {
            this.quarantineRejectPolicy = quarantineRejectPolicy;
        }
    }
}
