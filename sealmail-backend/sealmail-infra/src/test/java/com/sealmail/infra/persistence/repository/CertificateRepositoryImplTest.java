package com.sealmail.infra.persistence.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateBindingRepository;
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
import java.util.Optional;
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

    @Test
    void findTrustedForEncryptionUsesExplicitBindingWhenPresent() throws Exception {
        Certificate root = issueRoot(true);
        Certificate intermediate = issueIntermediate(root, true);
        Certificate first = issueLeaf(intermediate, true);
        Certificate bound = issueLeaf(intermediate, true);
        CertificateBindingRepository bindingRepository = mock(CertificateBindingRepository.class);
        when(bindingRepository.findActiveByOwnerAndPurpose(
                org.mockito.ArgumentMatchers.eq(bound.getOwner()),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(com.sealmail.domain.certificate.CertificateBinding.restore(
                        "binding-1",
                        bound.getOwner().getDomain(),
                        bound.getOwner(),
                        bound.getId(),
                        com.sealmail.domain.certificate.CertificateBindingPurpose.ENCRYPTION,
                        true,
                        Instant.now(),
                        Instant.now())));

        CertificateRepositoryImpl repository = repositoryBackedBy(
                List.of(mapper.toEntity(first)),
                List.of(mapper.toEntity(intermediate), mapper.toEntity(root)),
                bindingRepository);
        EntityManager entityManager = entityManagerWith(List.of(
                mapper.toEntity(bound),
                mapper.toEntity(intermediate),
                mapper.toEntity(root)));
        @SuppressWarnings("unchecked")
        TypedQuery<CertificateEntity> query = mock(TypedQuery.class);
        when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(mapper.toEntity(first)));
        when(entityManager.createQuery(anyString(), eq(CertificateEntity.class))).thenReturn(query);
        ReflectionTestUtils.setField(repository, "entityManager", entityManager);

        List<Certificate> result = repository.findTrustedForEncryption(bound.getOwner());

        assertEquals(1, result.size());
        assertEquals(bound.getId().getThumbprint(), result.getFirst().getId().getThumbprint());
    }

    private CertificateRepositoryImpl repositoryBackedBy(
            List<CertificateEntity> trustedQueryResult,
            List<CertificateEntity> issuers) {
        CertificateBindingRepository bindingRepository = mock(CertificateBindingRepository.class);
        when(bindingRepository.findActiveByOwnerAndPurpose(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());
        return repositoryBackedBy(trustedQueryResult, issuers, bindingRepository);
    }

    private CertificateRepositoryImpl repositoryBackedBy(
            List<CertificateEntity> trustedQueryResult,
            List<CertificateEntity> issuers,
            CertificateBindingRepository bindingRepository) {
        @SuppressWarnings("unchecked")
        TypedQuery<CertificateEntity> query = mock(TypedQuery.class);
        when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
        when(query.getResultList()).thenReturn(trustedQueryResult);

        EntityManager entityManager = entityManagerWith(issuers);
        when(entityManager.createQuery(anyString(), eq(CertificateEntity.class))).thenReturn(query);

        CertificateRepositoryImpl repository = new CertificateRepositoryImpl(
                mapper,
                mock(DomainEventPublisher.class),
                bindingRepository);
        ReflectionTestUtils.setField(repository, "entityManager", entityManager);
        return repository;
    }

    private EntityManager entityManagerWith(List<CertificateEntity> certificates) {
        EntityManager entityManager = mock(EntityManager.class);
        for (CertificateEntity certificate : certificates) {
            when(entityManager.find(CertificateEntity.class, certificate.getThumbprint())).thenReturn(certificate);
        }
        return entityManager;
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
