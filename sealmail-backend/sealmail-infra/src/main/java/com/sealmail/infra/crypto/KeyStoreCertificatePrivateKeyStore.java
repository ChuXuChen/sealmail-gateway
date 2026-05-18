package com.sealmail.infra.crypto;

import com.sealmail.domain.certificate.spi.CertificatePrivateKeySink;
import org.springframework.stereotype.Component;

@Component
public class KeyStoreCertificatePrivateKeyStore implements CertificatePrivateKeySink {

    private static final String PREFIX = "keystore:certificate:";

    private final KeyStoreService keyStoreService;

    public KeyStoreCertificatePrivateKeyStore(KeyStoreService keyStoreService) {
        this.keyStoreService = keyStoreService;
    }

    @Override
    public String store(String ownerEmail, String certificateThumbprint, String certificatePem, String privateKeyPem) {
        String alias = alias(certificateThumbprint);
        keyStoreService.storePemKeyPair(alias, privateKeyPem, certificatePem);
        return PREFIX + certificateThumbprint;
    }

    private String alias(String thumbprint) {
        return "certificate:" + thumbprint;
    }
}
