package com.sealmail.infra.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sealmail.security")
public class SecurityProperties {

    private final Crl crl = new Crl();
    private final Ocsp ocsp = new Ocsp();

    public boolean isCrlEnabled() {
        return crl.isEnabled();
    }

    public void setCrlEnabled(boolean crlEnabled) {
        this.crl.setEnabled(crlEnabled);
    }

    public boolean isOcspEnabled() {
        return ocsp.isEnabled();
    }

    public void setOcspEnabled(boolean ocspEnabled) {
        this.ocsp.setEnabled(ocspEnabled);
    }

    public int getOcspTimeout() {
        return ocsp.getTimeout();
    }

    public void setOcspTimeout(int ocspTimeout) {
        this.ocsp.setTimeout(ocspTimeout);
    }

    public Crl getCrl() {
        return crl;
    }

    public Ocsp getOcsp() {
        return ocsp;
    }

    public static class Crl {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Ocsp {
        private boolean enabled = true;
        private int timeout = 10000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }
    }
}
