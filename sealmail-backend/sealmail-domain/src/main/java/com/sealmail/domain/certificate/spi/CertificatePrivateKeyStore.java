package com.sealmail.domain.certificate.spi;

import java.util.Optional;

/**
 * Stores certificate private key material outside the relational certificate row
 * and returns a stable reference that may be persisted with the certificate.
 */
public interface CertificatePrivateKeyStore {

    String store(String ownerEmail, String certificateThumbprint, String certificatePem, String privateKeyPem);

    Optional<String> resolve(String privateKeyRef);
}
