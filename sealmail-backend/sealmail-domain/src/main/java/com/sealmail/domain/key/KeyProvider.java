package com.sealmail.domain.key;

import com.sealmail.domain.certificate.spi.CertificateCryptoPort;

public interface KeyProvider {

    String name();

    String storePrivateKey(String ownerEmail, String certificateId, String certificatePem, String privateKeyPem);

    boolean exists(String providerRef);

    KeyOperationResult signSmime(KeyRecord keyRecord, byte[] mimeMessage, String certificatePem);

    KeyOperationResult decryptSmime(KeyRecord keyRecord, byte[] encryptedMessage, String certificatePem);

    ManagedCertificateMaterial issueSelfSigned(IssueSelfSignedManagedCommand command);

    ManagedCertificateMaterial issueWithIssuer(IssueWithManagedIssuerCommand command, KeyRecord issuerKey);

    CertificateCryptoPort.CertificateDescriptor signCsr(SignCsrManagedCommand command, KeyRecord issuerKey);

    CertificateCryptoPort.CrlContent generateCrl(GenerateManagedCrlCommand command, KeyRecord caKey);

    record ManagedCertificateMaterial(
            CertificateCryptoPort.CertificateDescriptor certificate,
            String privateKeyProviderRef,
            String publicKeyFormat
    ) {
    }

    record IssueSelfSignedManagedCommand(
            String ownerEmail,
            String subjectDn,
            String algorithm,
            int validityDays,
            boolean ca,
            int pathLenConstraint,
            java.util.Set<String> extendedKeyUsages,
            String crlDistributionPointUrl,
            KeyPurpose purpose
    ) {
    }

    record IssueWithManagedIssuerCommand(
            String ownerEmail,
            String subjectDn,
            String subjectAlgorithm,
            String issuerCertificatePem,
            int validityDays,
            boolean ca,
            int pathLenConstraint,
            java.util.Set<String> extendedKeyUsages,
            String crlDistributionPointUrl,
            KeyPurpose purpose
    ) {
    }

    record SignCsrManagedCommand(
            String csrPem,
            String issuerCertificatePem,
            int validityDays,
            String crlDistributionPointUrl
    ) {
    }

    record GenerateManagedCrlCommand(
            String caCertificatePem,
            java.time.Instant thisUpdate,
            java.time.Instant nextUpdate,
            java.util.List<CertificateCryptoPort.CrlEntry> revokedEntries
    ) {
    }
}
