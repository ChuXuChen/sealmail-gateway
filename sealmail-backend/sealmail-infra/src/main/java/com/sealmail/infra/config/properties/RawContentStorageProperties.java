package com.sealmail.infra.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sealmail.storage.raw-content")
public class RawContentStorageProperties {

    private Encryption encryption = new Encryption();

    public Encryption getEncryption() {
        return encryption;
    }

    public void setEncryption(Encryption encryption) {
        this.encryption = encryption == null ? new Encryption() : encryption;
    }

    public static class Encryption {
        private boolean enabled = true;
        private String keyRef = "env:SEALMAIL_RAW_CONTENT_ENCRYPTION_KEY";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKeyRef() {
            return keyRef;
        }

        public void setKeyRef(String keyRef) {
            this.keyRef = keyRef;
        }
    }
}
