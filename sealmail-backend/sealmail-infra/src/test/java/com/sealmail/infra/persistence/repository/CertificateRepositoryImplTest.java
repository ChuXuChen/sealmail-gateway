package com.sealmail.infra.persistence.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.app.usecase.certificate.CertificateCryptoService;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.CertificateEntity;
import com.sealmail.infra.persistence.mapper.CertificateMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CertificateRepositoryImplTest {

    private final CertificateCryptoService cryptoService = new CertificateCryptoService();
    private final CertificateMapper mapper = new CertificateMapper(new ObjectMapper());

    @Test
    void findTrustedForEncryptionFiltersCertificateWhenIssuerChainIsUntrusted() throws Exception {
        Certificate root = issueRoot(false);
        Certificate intermediate = issueIntermediate(root, true);
        Certificate leaf = issueLeaf(intermediate, true);

        CertificateRepositoryImpl repository = repositoryBackedBy(
                List.of(mapper.toEntity(leaf)),
                List.of(mapper.toEntity(intermediate), mapper.toEntity(root)));

        List<Certificate> result = repository.findTrustedForEncryption(leaf.getOwner());

        assertEquals(0, result.size());
    }

    @Test
    void findTrustedForEncryptionReturnsCertificateWhenFullIssuerChainIsUsable() throws Exception {
        Certificate root = issueRoot(true);
        Certificate intermediate = issueIntermediate(root, true);
        Certificate leaf = issueLeaf(intermediate, true);

        CertificateRepositoryImpl repository = repositoryBackedBy(
                List.of(mapper.toEntity(leaf)),
                List.of(mapper.toEntity(intermediate), mapper.toEntity(root)));

        List<Certificate> result = repository.findTrustedForEncryption(leaf.getOwner());

        assertEquals(1, result.size());
        assertEquals(leaf.getId().getThumbprint(), result.getFirst().getId().getThumbprint());
    }

    private CertificateRepositoryImpl repositoryBackedBy(
            List<CertificateEntity> trustedQueryResult,
            List<CertificateEntity> issuers) {
        @SuppressWarnings("unchecked")
        TypedQuery<CertificateEntity> query = mock(TypedQuery.class);
        when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
        when(query.getResultList()).thenReturn(trustedQueryResult);

        EntityManager entityManager = mock(EntityManager.class);
        when(entityManager.createQuery(anyString(), eq(CertificateEntity.class))).thenReturn(query);
        for (CertificateEntity issuer : issuers) {
            when(entityManager.find(CertificateEntity.class, issuer.getThumbprint())).thenReturn(issuer);
        }

        CertificateRepositoryImpl repository = new CertificateRepositoryImpl(
                mapper,
                mock(DomainEventPublisher.class));
        ReflectionTestUtils.setField(repository, "entityManager", entityManager);
        return repository;
    }

    private Certificate issueRoot(boolean trusted) throws Exception {
        KeyPair keyPair = cryptoService.generateKeyPair("RSA");
        X509Certificate x509 = cryptoService.issue(CertificateCryptoService.CertSpec.builder()
                .subjectPubKey(keyPair.getPublic())
                .subjectDn("CN=Root, O=SealMail, C=CN")
                .subjectAlgorithm("RSA")
                .issuerDn("CN=Root, O=SealMail, C=CN")
                .issuerPrivKey(keyPair.getPrivate())
                .issuerPubKey(keyPair.getPublic())
                .validityDays(3650)
                .ca(true)
                .pathLenConstraint(1)
                .build());
        Certificate cert = cryptoService.toIssuedDomainCertificate(
                new CertificateId(cryptoService.computeThumbprint(x509)),
                new EmailAddress("root@example.com"),
                x509,
                "RSA");
        cert.setPrivateKeyData(cryptoService.privateKeyToPem(keyPair.getPrivate()));
        cert.markAsCA(1);
        if (trusted) {
            cert.trust();
        }
        return cert;
    }

    private Certificate issueIntermediate(Certificate root, boolean trusted) throws Exception {
        KeyPair keyPair = cryptoService.generateKeyPair("RSA");
        X509Certificate rootX509 = cryptoService.parseCertificate(root.getPemContent());
        X509Certificate x509 = cryptoService.issue(CertificateCryptoService.CertSpec.builder()
                .subjectPubKey(keyPair.getPublic())
                .subjectDn("CN=Intermediate, O=SealMail, C=CN")
                .subjectAlgorithm("RSA")
                .issuerDn(rootX509.getSubjectX500Principal().getName())
                .issuerPrivKey(cryptoService.parsePrivateKey(root.getPrivateKeyData()))
                .issuerPubKey(rootX509.getPublicKey())
                .validityDays(1825)
                .ca(true)
                .pathLenConstraint(0)
                .build());
        Certificate cert = cryptoService.toIssuedDomainCertificate(
                new CertificateId(cryptoService.computeThumbprint(x509)),
                new EmailAddress("intermediate@example.com"),
                x509,
                "RSA");
        cert.setPrivateKeyData(cryptoService.privateKeyToPem(keyPair.getPrivate()));
        cert.markAsCA(0);
        cert.setIssuerCertId(root.getId().getThumbprint());
        if (trusted) {
            cert.trust();
        }
        return cert;
    }

    private Certificate issueLeaf(Certificate intermediate, boolean trusted) throws Exception {
        KeyPair keyPair = cryptoService.generateKeyPair("RSA");
        X509Certificate caX509 = cryptoService.parseCertificate(intermediate.getPemContent());
        X509Certificate x509 = cryptoService.issue(CertificateCryptoService.CertSpec.builder()
                .subjectPubKey(keyPair.getPublic())
                .subjectDn("CN=user@example.com")
                .subjectAlgorithm("RSA")
                .issuerDn(caX509.getSubjectX500Principal().getName())
                .issuerPrivKey(cryptoService.parsePrivateKey(intermediate.getPrivateKeyData()))
                .issuerPubKey(caX509.getPublicKey())
                .validityDays(365)
                .ca(false)
                .ekus(Set.of(CertificateCryptoService.EKU_EMAIL_PROTECTION))
                .build());
        Certificate cert = cryptoService.toIssuedDomainCertificate(
                new CertificateId(cryptoService.computeThumbprint(x509)),
                new EmailAddress("user@example.com"),
                x509,
                "RSA");
        cert.setIssuerCertId(intermediate.getId().getThumbprint());
        if (trusted) {
            cert.trust();
        }
        return cert;
    }
}
