package com.sealmail.infra.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sealmail.dlp")
public class DlpProperties {

    private boolean quarantineEncryptionFailures = true;

    public boolean isQuarantineEncryptionFailures() {
        return quarantineEncryptionFailures;
    }

    public void setQuarantineEncryptionFailures(boolean quarantineEncryptionFailures) {
        this.quarantineEncryptionFailures = quarantineEncryptionFailures;
    }
}
