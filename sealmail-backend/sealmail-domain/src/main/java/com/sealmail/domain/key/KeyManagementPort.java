package com.sealmail.domain.key;

import com.sealmail.domain.certificate.spi.CertificateCryptoPort;
import com.sealmail.domain.shared.model.EmailAddress;

import java.util.Optional;

public interface KeyManagementPort {

    KeyRecord importCertificateKey(EmailAddress owner,
                                   String algorithm,
                                   KeyPurpose purpose,
                                   String certificateId,
                                   String certificatePem,
                                   String privateKeyPem);

    Optional<KeyRecord> findActiveKeyForCertificate(String certificateId);

    KeyRecord requireActiveKeyForCertificate(String certificateId, String missingMessage);

    KeyOperationResult signSmimeForCertificate(String certificateId, byte[] mimeMessage, String certificatePem);

    KeyOperationResult decryptSmimeForCertificate(String certificateId, byte[] encryptedMessage, String certificatePem);

    ManagedCertificateMaterial issueSelfSigned(KeyProvider.IssueSelfSignedManagedCommand command);

    ManagedCertificateMaterial issueWithIssuer(KeyProvider.IssueWithManagedIssuerCommand command, String issuerKeyId);

    CertificateCryptoPort.CertificateDescriptor signCsr(KeyProvider.SignCsrManagedCommand command, String issuerKeyId);

    CertificateCryptoPort.CrlContent generateCrl(KeyProvider.GenerateManagedCrlCommand command, String caKeyId);

    record ManagedCertificateMaterial(
            CertificateCryptoPort.CertificateDescriptor certificate,
            KeyRecord keyRecord,
            String publicKeyFormat
    ) {
    }
}
