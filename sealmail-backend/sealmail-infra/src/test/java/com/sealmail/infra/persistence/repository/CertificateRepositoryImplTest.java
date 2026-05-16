package com.sealmail.infra.persistence.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.KeyUsage;
import com.sealmail.domain.certificate.ValidityPeriod;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.CertificateEntity;
import com.sealmail.infra.persistence.mapper.CertificateMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CertificateRepositoryImplTest {

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

    private Certificate issueRoot(boolean trusted) {
        Certificate cert = certificate("root@example.com", "CN=Root", "CN=Root");
        cert.markAsCA(1);
        if (trusted) {
            cert.trust();
        }
        return cert;
    }

    private Certificate issueIntermediate(Certificate root, boolean trusted) {
        Certificate cert = certificate("intermediate@example.com", "CN=Intermediate", root.getSubjectDn());
        cert.markAsCA(0);
        cert.setIssuerCertId(root.getId().getThumbprint());
        if (trusted) {
            cert.trust();
        }
        return cert;
    }

    private Certificate issueLeaf(Certificate intermediate, boolean trusted) {
        Certificate cert = certificate("user@example.com", "CN=user@example.com", intermediate.getSubjectDn());
        cert.setIssuerCertId(intermediate.getId().getThumbprint());
        if (trusted) {
            cert.trust();
        }
        return cert;
    }

    private Certificate certificate(String owner, String subjectDn, String issuerDn) {
        Certificate cert = Certificate.importCertificate(
                new CertificateId(UUID.randomUUID().toString()),
                new EmailAddress(owner),
                "test-pem-" + UUID.randomUUID(),
                new ValidityPeriod(Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600)),
                EnumSet.of(KeyUsage.ENCRYPTION),
                issuerDn,
                subjectDn,
                BigInteger.ONE,
                "ski-" + UUID.randomUUID()
        );
        cert.setAlgorithm("RSA");
        return cert;
    }
}
