package com.sealmail.infra.persistence.repository;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateBinding;
import com.sealmail.domain.certificate.CertificateBindingPurpose;
import com.sealmail.domain.certificate.CertificateBindingRepository;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.event.CertificateDeleted;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.CertificateEntity;
import com.sealmail.infra.persistence.mapper.CertificateMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class CertificateRepositoryImpl implements CertificateRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private final CertificateMapper mapper;
    private final DomainEventPublisher domainEventPublisher;
    private final CertificateBindingRepository certificateBindingRepository;

    public CertificateRepositoryImpl(CertificateMapper mapper,
                                     DomainEventPublisher domainEventPublisher,
                                     CertificateBindingRepository certificateBindingRepository) {
        this.mapper = mapper;
        this.domainEventPublisher = domainEventPublisher;
        this.certificateBindingRepository = certificateBindingRepository;
    }

    @Override
    public Certificate save(Certificate certificate) {
        CertificateEntity entity = mapper.toEntity(certificate);
        entity.setUpdatedAt(Instant.now());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(Instant.now());
        }

        CertificateEntity existing = entityManager.find(CertificateEntity.class, entity.getThumbprint());
        if (existing != null) {
            entity.setCreatedAt(existing.getCreatedAt());
            entity.setVersion(existing.getVersion());
            entityManager.merge(entity);
        } else {
            entityManager.persist(entity);
        }
        domainEventPublisher.publishEvents(certificate);
        return certificate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Certificate> findById(CertificateId id) {
        CertificateEntity entity = entityManager.find(CertificateEntity.class, id.getThumbprint());
        return Optional.ofNullable(entity).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findAll() {
        TypedQuery<CertificateEntity> query = entityManager.createQuery(
                "SELECT c FROM CertificateEntity c ORDER BY c.createdAt DESC",
                CertificateEntity.class
        );
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findByOwner(EmailAddress owner) {
        TypedQuery<CertificateEntity> query = entityManager.createQuery(
                "SELECT c FROM CertificateEntity c WHERE c.ownerEmail = :email",
                CertificateEntity.class
        );
        query.setParameter("email", owner.getValue());
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findByIssuerCertId(String issuerCertId) {
        TypedQuery<CertificateEntity> query = entityManager.createQuery(
                "SELECT c FROM CertificateEntity c WHERE c.issuerCertId = :issuer ORDER BY c.createdAt DESC",
                CertificateEntity.class);
        query.setParameter("issuer", issuerCertId);
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findAllCAs() {
        TypedQuery<CertificateEntity> query = entityManager.createQuery(
                "SELECT c FROM CertificateEntity c WHERE c.ca = true ORDER BY c.createdAt DESC",
                CertificateEntity.class);
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findAllEndEntities() {
        TypedQuery<CertificateEntity> query = entityManager.createQuery(
                "SELECT c FROM CertificateEntity c WHERE c.ca = false ORDER BY c.createdAt DESC",
                CertificateEntity.class);
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findTrustedForEncryption(EmailAddress owner) {
        Optional<CertificateBinding> binding = certificateBindingRepository.findActiveByOwnerAndPurpose(
                owner,
                CertificateBindingPurpose.ENCRYPTION);
        if (binding.isPresent()) {
            return boundCertificate(binding.get(), owner, CertificateBindingPurpose.ENCRYPTION);
        }
        TypedQuery<CertificateEntity> query = entityManager.createQuery(
                "SELECT c FROM CertificateEntity c WHERE c.ownerEmail = :email " +
                        "AND c.trusted = true AND c.revoked = false AND c.notAfter > :now " +
                        "ORDER BY c.notAfter DESC",
                CertificateEntity.class
        );
        query.setParameter("email", owner.getValue());
        query.setParameter("now", Instant.now());
        return query.getResultList().stream()
                .map(mapper::toDomain)
                .filter(Certificate::isSuitableForEncryption)
                .filter(this::isChainTrustedAndUsable)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findTrustedForSigning(EmailAddress owner) {
        Optional<CertificateBinding> binding = certificateBindingRepository.findActiveByOwnerAndPurpose(
                owner,
                CertificateBindingPurpose.SIGNING);
        if (binding.isPresent()) {
            return boundCertificate(binding.get(), owner, CertificateBindingPurpose.SIGNING);
        }
        TypedQuery<CertificateEntity> query = entityManager.createQuery(
                "SELECT c FROM CertificateEntity c WHERE c.ownerEmail = :email " +
                        "AND c.trusted = true AND c.revoked = false AND c.notAfter > :now " +
                        "ORDER BY c.hasPrivateKey DESC, c.createdAt DESC",
                CertificateEntity.class
        );
        query.setParameter("email", owner.getValue());
        query.setParameter("now", Instant.now());
        return query.getResultList().stream()
                .map(mapper::toDomain)
                .filter(Certificate::isSuitableForSigning)
                .filter(this::isChainTrustedAndUsable)
                .toList();
    }

    @Override
    public void deleteById(CertificateId id) {
        CertificateEntity entity = entityManager.find(CertificateEntity.class, id.getThumbprint());
        if (entity != null) {
            EmailAddress owner = new EmailAddress(entity.getOwnerEmail());
            certificateBindingRepository.deleteByCertificateId(id);
            entityManager.remove(entity);
            domainEventPublisher.publishEvent(new CertificateDeleted(id, owner));
        }
    }

    /**
     * 直接按邮箱查找证书实体（用于 S/MIME 处理）
     */
    @Transactional(readOnly = true)
    public List<CertificateEntity> findByOwnerEmail(String email) {
        TypedQuery<CertificateEntity> query = entityManager.createQuery(
                "SELECT c FROM CertificateEntity c WHERE c.ownerEmail = :email " +
                        "AND c.revoked = false ORDER BY c.createdAt DESC",
                CertificateEntity.class
        );
        query.setParameter("email", email);
        return query.getResultList();
    }

    /**
     * 查找有私钥的签名证书
     */
    @Transactional(readOnly = true)
    public List<CertificateEntity> findSigningCertificates(String email) {
        TypedQuery<CertificateEntity> query = entityManager.createQuery(
                "SELECT c FROM CertificateEntity c WHERE c.ownerEmail = :email " +
                        "AND c.hasPrivateKey = true AND c.revoked = false AND c.trusted = true",
                CertificateEntity.class
        );
        query.setParameter("email", email);
        return query.getResultList();
    }

    private boolean isChainTrustedAndUsable(Certificate certificate) {
        Certificate current = certificate;
        java.util.Set<String> visited = new java.util.HashSet<>();
        Instant now = Instant.now();

        while (current != null) {
            String currentId = current.getId().getThumbprint();
            if (!visited.add(currentId)) {
                return false;
            }
            if (!current.isTrusted() || current.isRevoked() || !current.getValidity().isValidAt(now)) {
                return false;
            }

            String issuerCertId = current.getIssuerCertId();
            if (issuerCertId == null || issuerCertId.isBlank()) {
                return true;
            }
            CertificateEntity issuer = entityManager.find(CertificateEntity.class, issuerCertId);
            if (issuer == null) {
                return false;
            }
            current = mapper.toDomain(issuer);
        }

        return false;
    }

    private List<Certificate> boundCertificate(CertificateBinding binding,
                                               EmailAddress owner,
                                               CertificateBindingPurpose purpose) {
        CertificateEntity entity = entityManager.find(
                CertificateEntity.class,
                binding.getCertificateId().getThumbprint());
        if (entity == null) {
            return List.of();
        }
        Certificate certificate = mapper.toDomain(entity);
        if (!certificate.getOwner().equals(owner) || !isChainTrustedAndUsable(certificate)) {
            return List.of();
        }
        if (purpose == CertificateBindingPurpose.ENCRYPTION && !certificate.isSuitableForEncryption()) {
            return List.of();
        }
        if (purpose == CertificateBindingPurpose.SIGNING && !certificate.isSuitableForSigning()) {
            return List.of();
        }
        return List.of(certificate);
    }
}
