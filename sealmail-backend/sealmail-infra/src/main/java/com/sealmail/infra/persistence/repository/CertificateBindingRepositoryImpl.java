package com.sealmail.infra.persistence.repository;

import com.sealmail.domain.certificate.CertificateBinding;
import com.sealmail.domain.certificate.CertificateBindingPurpose;
import com.sealmail.domain.certificate.CertificateBindingRepository;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.event.CertificateBindingChanged;
import com.sealmail.domain.policy.DomainName;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.CertificateBindingEntity;
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
public class CertificateBindingRepositoryImpl implements CertificateBindingRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private final DomainEventPublisher domainEventPublisher;

    public CertificateBindingRepositoryImpl(DomainEventPublisher domainEventPublisher) {
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    public CertificateBinding save(CertificateBinding binding) {
        CertificateBindingEntity entity = toEntity(binding);
        CertificateBindingEntity existing = entityManager.find(CertificateBindingEntity.class, entity.getId());
        if (existing != null) {
            entity.setCreatedAt(existing.getCreatedAt());
            entity.setVersion(existing.getVersion());
            entityManager.merge(entity);
        } else {
            entityManager.persist(entity);
        }
        domainEventPublisher.publishEvents(binding);
        return binding;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CertificateBinding> findById(String id) {
        return Optional.ofNullable(entityManager.find(CertificateBindingEntity.class, id))
                .map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CertificateBinding> findByOwnerAndPurpose(EmailAddress owner, CertificateBindingPurpose purpose) {
        TypedQuery<CertificateBindingEntity> query = entityManager.createQuery(
                "SELECT b FROM CertificateBindingEntity b WHERE b.ownerEmail = :owner AND b.purpose = :purpose",
                CertificateBindingEntity.class);
        query.setParameter("owner", owner.getValue());
        query.setParameter("purpose", purpose.name());
        return query.getResultList().stream().findFirst().map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CertificateBinding> findActiveByOwnerAndPurpose(EmailAddress owner,
                                                                    CertificateBindingPurpose purpose) {
        TypedQuery<CertificateBindingEntity> query = entityManager.createQuery(
                "SELECT b FROM CertificateBindingEntity b WHERE b.ownerEmail = :owner " +
                        "AND b.purpose = :purpose AND b.enabled = true",
                CertificateBindingEntity.class);
        query.setParameter("owner", owner.getValue());
        query.setParameter("purpose", purpose.name());
        return query.getResultList().stream().findFirst().map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CertificateBinding> findAll() {
        return entityManager.createQuery(
                        "SELECT b FROM CertificateBindingEntity b ORDER BY b.domainName, b.ownerEmail, b.purpose",
                        CertificateBindingEntity.class)
                .getResultList()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CertificateBinding> findByDomain(String domain) {
        TypedQuery<CertificateBindingEntity> query = entityManager.createQuery(
                "SELECT b FROM CertificateBindingEntity b WHERE b.domainName = :domain " +
                        "ORDER BY b.ownerEmail, b.purpose",
                CertificateBindingEntity.class);
        query.setParameter("domain", DomainName.requireValid(domain));
        return query.getResultList().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CertificateBinding> findByOwner(EmailAddress owner) {
        TypedQuery<CertificateBindingEntity> query = entityManager.createQuery(
                "SELECT b FROM CertificateBindingEntity b WHERE b.ownerEmail = :owner ORDER BY b.purpose",
                CertificateBindingEntity.class);
        query.setParameter("owner", owner.getValue());
        return query.getResultList().stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(String id) {
        CertificateBindingEntity entity = entityManager.find(CertificateBindingEntity.class, id);
        if (entity == null) {
            return;
        }
        entityManager.remove(entity);
        domainEventPublisher.publishEvent(new CertificateBindingChanged(
                entity.getId(),
                entity.getDomainName(),
                entity.getOwnerEmail(),
                entity.getCertificateId(),
                entity.getPurpose(),
                "DELETE"));
    }

    @Override
    public void deleteByCertificateId(CertificateId certificateId) {
        if (certificateId == null) {
            throw new IllegalArgumentException("Certificate id cannot be null");
        }
        List<CertificateBindingEntity> bindings = entityManager.createQuery(
                        "SELECT b FROM CertificateBindingEntity b WHERE b.certificateId = :certificateId",
                        CertificateBindingEntity.class)
                .setParameter("certificateId", certificateId.getThumbprint())
                .getResultList();
        for (CertificateBindingEntity binding : bindings) {
            entityManager.remove(binding);
            domainEventPublisher.publishEvent(new CertificateBindingChanged(
                    binding.getId(),
                    binding.getDomainName(),
                    binding.getOwnerEmail(),
                    binding.getCertificateId(),
                    binding.getPurpose(),
                    "DELETE"));
        }
    }

    private CertificateBindingEntity toEntity(CertificateBinding binding) {
        CertificateBindingEntity entity = new CertificateBindingEntity();
        entity.setId(binding.getId());
        entity.setDomainName(binding.getDomain());
        entity.setOwnerEmail(binding.getOwner().getValue());
        entity.setPurpose(binding.getPurpose().name());
        entity.setCertificateId(binding.getCertificateId().getThumbprint());
        entity.setEnabled(binding.isEnabled());
        entity.setCreatedAt(binding.getCreatedAt());
        entity.setUpdatedAt(binding.getUpdatedAt() != null ? binding.getUpdatedAt() : Instant.now());
        return entity;
    }

    private CertificateBinding toDomain(CertificateBindingEntity entity) {
        return CertificateBinding.restore(
                entity.getId(),
                entity.getDomainName(),
                new EmailAddress(entity.getOwnerEmail()),
                new CertificateId(entity.getCertificateId()),
                CertificateBindingPurpose.valueOf(entity.getPurpose()),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
