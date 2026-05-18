package com.sealmail.infra.crypto;

import com.sealmail.domain.certificate.spi.CertificatePrivateKeyStore;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class KeyStoreCertificatePrivateKeyStore implements CertificatePrivateKeyStore {

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

    @Override
    public Optional<String> resolve(String privateKeyRef) {
        if (privateKeyRef == null || privateKeyRef.isBlank()) {
            return Optional.empty();
        }
        String value = privateKeyRef.trim();
        if (!value.startsWith(PREFIX)) {
            return Optional.empty();
        }
        return Optional.ofNullable(keyStoreService.getPrivateKeyPemByAlias(alias(value.substring(PREFIX.length()))))
                .filter(pem -> !pem.isBlank());
    }

    private String alias(String thumbprint) {
        return "certificate:" + thumbprint;
    }
}
