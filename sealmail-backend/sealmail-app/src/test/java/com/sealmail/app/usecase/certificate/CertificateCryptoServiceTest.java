package com.sealmail.app.usecase.certificate;

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CertificateCryptoServiceTest {

    private CertificateCryptoService service;

    @BeforeEach
    void setUp() {
        service = new CertificateCryptoService();
    }

    @Test
    void validateCsrAcceptsValidSignature() throws Exception {
        KeyPair keyPair = service.generateKeyPair("RSA");
        PKCS10CertificationRequest csr = buildCsr("CN=user@example.com", keyPair.getPublic(), keyPair.getPrivate(), "SHA256withRSA");

        assertDoesNotThrow(() -> service.validateCsr(csr));
    }

    @Test
    void validateCsrRejectsTamperedRequest() throws Exception {
        KeyPair signer = service.generateKeyPair("RSA");
        KeyPair subject = service.generateKeyPair("RSA");
        PKCS10CertificationRequest csr = buildCsr("CN=user@example.com", subject.getPublic(), signer.getPrivate(), "SHA256withRSA");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.validateCsr(csr));
        assertEquals("CSR signature is invalid", ex.getMessage());
    }

    @Test
    void validateCertificateMatchesPrivateKeyAcceptsMatchingRsaKey() throws Exception {
        KeyPair keyPair = service.generateKeyPair("RSA");
        X509Certificate cert = issueSelfSigned("CN=match.example", "RSA", keyPair);

        assertDoesNotThrow(() -> service.validateCertificateMatchesPrivateKey(cert, keyPair.getPrivate()));
    }

    @Test
    void validateCertificateMatchesPrivateKeyRejectsMismatchedSm2Key() throws Exception {
        KeyPair certKeyPair = service.generateKeyPair("SM2");
        KeyPair otherKeyPair = service.generateKeyPair("SM2");
        X509Certificate cert = issueSelfSigned("CN=sm2.example", "SM2", certKeyPair);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.validateCertificateMatchesPrivateKey(cert, otherKeyPair.getPrivate()));
        assertEquals("Private key does not match certificate public key", ex.getMessage());
    }

    @Test
    void isIssuedByAcceptsCertificateSignedByIssuer() throws Exception {
        KeyPair issuerKeyPair = service.generateKeyPair("RSA");
        X509Certificate issuer = service.issue(CertificateCryptoService.CertSpec.builder()
                .subjectPubKey(issuerKeyPair.getPublic())
                .subjectDn("CN=Issuer, O=SealMail, C=CN")
                .subjectAlgorithm("RSA")
                .issuerDn("CN=Issuer, O=SealMail, C=CN")
                .issuerPrivKey(issuerKeyPair.getPrivate())
                .issuerPubKey(issuerKeyPair.getPublic())
                .validityDays(3650)
                .ca(true)
                .pathLenConstraint(1)
                .build());
        KeyPair subjectKeyPair = service.generateKeyPair("RSA");
        X509Certificate subject = service.issue(CertificateCryptoService.CertSpec.builder()
                .subjectPubKey(subjectKeyPair.getPublic())
                .subjectDn("CN=user@example.com")
                .subjectAlgorithm("RSA")
                .issuerDn(issuer.getSubjectX500Principal().getName())
                .issuerPrivKey(issuerKeyPair.getPrivate())
                .issuerPubKey(issuer.getPublicKey())
                .validityDays(365)
                .ca(false)
                .ekus(Set.of(CertificateCryptoService.EKU_EMAIL_PROTECTION))
                .build());

        assertTrue(service.isIssuedBy(subject, issuer));
    }

    private PKCS10CertificationRequest buildCsr(String subjectDn, PublicKey publicKey, PrivateKey privateKey, String algorithm)
            throws Exception {
        PKCS10CertificationRequestBuilder builder =
                new JcaPKCS10CertificationRequestBuilder(new X500Name(subjectDn), publicKey);
        return builder.build(new JcaContentSignerBuilder(algorithm).setProvider("BC").build(privateKey));
    }

    private X509Certificate issueSelfSigned(String subjectDn, String algorithm, KeyPair keyPair) throws Exception {
        return service.issue(CertificateCryptoService.CertSpec.builder()
                .subjectPubKey(keyPair.getPublic())
                .subjectDn(subjectDn)
                .subjectAlgorithm(algorithm)
                .issuerDn(subjectDn)
                .issuerPrivKey(keyPair.getPrivate())
                .issuerPubKey(keyPair.getPublic())
                .validityDays(365)
                .ca(false)
                .ekus(Set.of(CertificateCryptoService.EKU_EMAIL_PROTECTION))
                .build());
    }
}
