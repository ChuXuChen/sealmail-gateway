package com.sealmail.app.usecase.certificate;

import com.sealmail.domain.certificate.spi.CertificateCryptoPort;
import com.sealmail.domain.key.KeyManagementPort;
import com.sealmail.domain.key.KeyOperationResult;
import com.sealmail.domain.key.KeyProvider;
import com.sealmail.domain.key.KeyPurpose;
import com.sealmail.domain.key.KeyRecord;
import com.sealmail.domain.shared.model.EmailAddress;

import java.util.Optional;

final class FakeKeyManagementPort implements KeyManagementPort {

    private final java.util.Map<String, KeyRecord> keysByCertificateId = new java.util.LinkedHashMap<>();

    @Override
    public KeyRecord importCertificateKey(EmailAddress owner,
                                          String algorithm,
                                          KeyPurpose purpose,
                                          String certificateId,
                                          String certificatePem,
                                          String privateKeyPem) {
        KeyRecord keyRecord = KeyRecord.active(
                "key-" + certificateId,
                owner,
                algorithm,
                purpose,
                "TEST",
                "test-provider:" + certificateId,
                certificateId);
        keysByCertificateId.put(certificateId, keyRecord);
        return keyRecord;
    }

    void addKey(CertificateCryptoPort.CertificateDescriptor descriptor, EmailAddress owner, KeyPurpose purpose) {
        importCertificateKey(owner, descriptor.algorithm(), purpose,
                descriptor.thumbprint(), descriptor.pemContent(), "test-private-key");
    }

    @Override
    public Optional<KeyRecord> findActiveKeyForCertificate(String certificateId) {
        return Optional.ofNullable(keysByCertificateId.get(certificateId));
    }

    @Override
    public KeyRecord requireActiveKeyForCertificate(String certificateId, String missingMessage) {
        return findActiveKeyForCertificate(certificateId)
                .orElseThrow(() -> new IllegalStateException(missingMessage));
    }

    @Override
    public KeyOperationResult signSmimeForCertificate(String certificateId, byte[] mimeMessage, String certificatePem) {
        throw new UnsupportedOperationException();
    }

    @Override
    public KeyOperationResult decryptSmimeForCertificate(String certificateId, byte[] encryptedMessage, String certificatePem) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ManagedCertificateMaterial issueSelfSigned(KeyProvider.IssueSelfSignedManagedCommand command) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ManagedCertificateMaterial issueWithIssuer(KeyProvider.IssueWithManagedIssuerCommand command, String issuerKeyId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CertificateCryptoPort.CertificateDescriptor signCsr(KeyProvider.SignCsrManagedCommand command, String issuerKeyId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CertificateCryptoPort.CrlContent generateCrl(KeyProvider.GenerateManagedCrlCommand command, String caKeyId) {
        throw new UnsupportedOperationException();
    }
}
