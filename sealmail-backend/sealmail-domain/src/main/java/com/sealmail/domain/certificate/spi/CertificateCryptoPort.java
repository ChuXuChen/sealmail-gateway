package com.sealmail.domain.certificate.spi;

import com.sealmail.domain.certificate.KeyUsage;
import com.sealmail.domain.certificate.ValidityPeriod;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Certificate and CRL cryptography port. Domain owns the contract; infrastructure
 * owns provider-specific parsing, signing and encoding.
 */
public interface CertificateCryptoPort {

    String EKU_EMAIL_PROTECTION = "1.3.6.1.5.5.7.3.4";

    CertificateMaterial issueSelfSigned(IssueSelfSignedCommand command);

    CertificateMaterial issueWithIssuer(IssueWithIssuerCommand command);

    CertificateMaterial generateTestMaterial();

    CryptoCapabilities cryptoCapabilities();

    CertificateDescriptor signCsr(SignCsrCommand command);

    CsrInfo validateCsr(String csrPem);

    CertificateDescriptor readCertificate(String certificatePem);

    void validateCertificateMatchesPrivateKey(String certificatePem, String privateKeyPem);

    boolean isSelfSigned(String certificatePem);

    boolean isIssuedBy(String subjectCertificatePem, String issuerCertificatePem);

    CrlContent normalizeAndValidateCrl(String caCertificatePem, String crlPem, String crlDerBase64);

    CrlContent generateCrl(GenerateCrlCommand command);

    record IssueSelfSignedCommand(
            String subjectDn,
            String algorithm,
            int validityDays,
            boolean ca,
            int pathLenConstraint,
            Set<String> extendedKeyUsages,
            String crlDistributionPointUrl
    ) {
    }

    record IssueWithIssuerCommand(
            String subjectDn,
            String subjectAlgorithm,
            String issuerCertificatePem,
            String issuerPrivateKeyPem,
            int validityDays,
            boolean ca,
            int pathLenConstraint,
            Set<String> extendedKeyUsages,
            String crlDistributionPointUrl
    ) {
    }

    record SignCsrCommand(
            String csrPem,
            String issuerCertificatePem,
            String issuerPrivateKeyPem,
            int validityDays,
            String crlDistributionPointUrl
    ) {
    }

    record GenerateCrlCommand(
            String caCertificatePem,
            String caPrivateKeyPem,
            Instant thisUpdate,
            Instant nextUpdate,
            List<CrlEntry> revokedEntries
    ) {
    }

    record CrlEntry(
            BigInteger serialNumber,
            Instant revocationDate,
            String reasonCode
    ) {
    }

    record CertificateMaterial(
            CertificateDescriptor certificate,
            String privateKeyPem,
            String publicKeyFormat
    ) {
    }

    record CertificateDescriptor(
            String pemContent,
            String algorithm,
            String thumbprint,
            ValidityPeriod validity,
            Set<KeyUsage> keyUsages,
            String issuerDn,
            String subjectDn,
            BigInteger serialNumber,
            String subjectKeyIdentifier,
            boolean ca,
            Integer pathLenConstraint,
            Set<String> extendedKeyUsages,
            String crlDistributionPointUrl
    ) {
    }

    record CsrInfo(
            String subjectDn,
            String ownerEmail,
            String algorithm
    ) {
    }

    record CrlContent(
            byte[] der,
            String pem
    ) {
    }

    record CryptoCapabilities(
            Map<String, String> supportedAlgorithms,
            String status
    ) {
    }
}
