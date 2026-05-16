package com.sealmail.infra.crypto;

import com.sealmail.domain.certificate.spi.CertificateCryptoPort;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BcCertificateCryptoPortTest {

    private final BcCertificateCryptoPort port = new BcCertificateCryptoPort();

    @Test
    void validateCsrAcceptsValidSignature() throws Exception {
        KeyPair keyPair = port.generateKeyPair("RSA");
        String csrPem = port.writeCsrPem(buildCsr("CN=user@example.com", keyPair, keyPair, "SHA256withRSA"));

        CertificateCryptoPort.CsrInfo info = assertDoesNotThrow(() -> port.validateCsr(csrPem));
        assertEquals("user@example.com", info.ownerEmail());
    }

    @Test
    void validateCsrRejectsTamperedRequest() throws Exception {
        KeyPair signer = port.generateKeyPair("RSA");
        KeyPair subject = port.generateKeyPair("RSA");
        String csrPem = port.writeCsrPem(buildCsr("CN=user@example.com", subject, signer, "SHA256withRSA"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> port.validateCsr(csrPem));
        assertEquals("CSR signature is invalid", ex.getMessage());
    }

    @Test
    void validateCertificateMatchesPrivateKeyAcceptsMatchingRsaKey() {
        CertificateCryptoPort.CertificateMaterial material = port.issueSelfSigned(new CertificateCryptoPort.IssueSelfSignedCommand(
                "CN=match.example",
                "RSA",
                365,
                false,
                0,
                Set.of(CertificateCryptoPort.EKU_EMAIL_PROTECTION),
                null));

        assertDoesNotThrow(() -> port.validateCertificateMatchesPrivateKey(
                material.certificate().pemContent(),
                material.privateKeyPem()));
    }

    @Test
    void validateCertificateMatchesPrivateKeyRejectsMismatchedSm2Key() {
        CertificateCryptoPort.CertificateMaterial certMaterial = port.issueSelfSigned(new CertificateCryptoPort.IssueSelfSignedCommand(
                "CN=sm2.example",
                "SM2",
                365,
                false,
                0,
                Set.of(CertificateCryptoPort.EKU_EMAIL_PROTECTION),
                null));
        CertificateCryptoPort.CertificateMaterial otherMaterial = port.issueSelfSigned(new CertificateCryptoPort.IssueSelfSignedCommand(
                "CN=other.example",
                "SM2",
                365,
                false,
                0,
                Set.of(CertificateCryptoPort.EKU_EMAIL_PROTECTION),
                null));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> port.validateCertificateMatchesPrivateKey(
                        certMaterial.certificate().pemContent(),
                        otherMaterial.privateKeyPem()));
        assertEquals("Private key does not match certificate public key", ex.getMessage());
    }

    @Test
    void isIssuedByAcceptsCertificateSignedByIssuer() {
        CertificateCryptoPort.CertificateMaterial issuer = port.issueSelfSigned(new CertificateCryptoPort.IssueSelfSignedCommand(
                "CN=Issuer, O=SealMail, C=CN",
                "RSA",
                3650,
                true,
                1,
                Set.of(),
                null));
        CertificateCryptoPort.CertificateMaterial subject = port.issueWithIssuer(new CertificateCryptoPort.IssueWithIssuerCommand(
                "CN=user@example.com",
                "RSA",
                issuer.certificate().pemContent(),
                issuer.privateKeyPem(),
                365,
                false,
                0,
                Set.of(CertificateCryptoPort.EKU_EMAIL_PROTECTION),
                null));

        assertTrue(port.isIssuedBy(subject.certificate().pemContent(), issuer.certificate().pemContent()));
        assertNotNull(subject.certificate().thumbprint());
    }

    private PKCS10CertificationRequest buildCsr(String subjectDn,
                                                KeyPair subject,
                                                KeyPair signer,
                                                String algorithm) throws Exception {
        PKCS10CertificationRequestBuilder builder =
                new JcaPKCS10CertificationRequestBuilder(new X500Name(subjectDn), subject.getPublic());
        return builder.build(new JcaContentSignerBuilder(algorithm).setProvider("BC").build(signer.getPrivate()));
    }
}
