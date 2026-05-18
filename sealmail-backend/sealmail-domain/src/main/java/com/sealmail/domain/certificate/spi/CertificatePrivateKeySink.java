package com.sealmail.domain.certificate.spi;

/**
 * Stores certificate private key material outside the relational certificate row
 * and returns a stable reference that may be persisted with the certificate.
 *
 * @deprecated Use KeyManagementPort. This legacy sink only stores material
 * and never exposes private key PEM to business code.
 */
@Deprecated(forRemoval = false)
public interface CertificatePrivateKeySink {

    String store(String ownerEmail, String certificateThumbprint, String certificatePem, String privateKeyPem);
}
