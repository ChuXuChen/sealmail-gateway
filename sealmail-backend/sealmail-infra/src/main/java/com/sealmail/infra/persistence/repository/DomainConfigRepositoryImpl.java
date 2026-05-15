package com.sealmail.infra.persistence.repository;

import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.DomainConfigRepository;
import com.sealmail.domain.policy.DomainName;
import com.sealmail.domain.policy.event.DomainConfigDeleted;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.DomainConfigEntity;
import com.sealmail.infra.persistence.mapper.DomainConfigMapper;
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
public class DomainConfigRepositoryImpl implements DomainConfigRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private final DomainConfigMapper mapper;
    private final DomainEventPublisher domainEventPublisher;

    public DomainConfigRepositoryImpl(DomainConfigMapper mapper,
                                      DomainEventPublisher domainEventPublisher) {
        this.mapper = mapper;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    public DomainConfig save(DomainConfig domainConfig) {
        DomainConfigEntity entity = mapper.toEntity(domainConfig);
        entity.setUpdatedAt(Instant.now());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(Instant.now());
        }

        DomainConfigEntity existing = entityManager.find(DomainConfigEntity.class, entity.getId());
        if (existing != null) {
            entity.setCreatedAt(existing.getCreatedAt());
            // @Version is not carried through the domain model — preserve it from the
            // managed entity so merge() doesn't trip the optimistic-lock check.
            entity.setVersion(existing.getVersion());
            entityManager.merge(entity);
        } else {
            entityManager.persist(entity);
        }
        domainEventPublisher.publishEvents(domainConfig);
        return domainConfig;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DomainConfig> findById(String id) {
        DomainConfigEntity entity = entityManager.find(DomainConfigEntity.class, id);
        return Optional.ofNullable(entity).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DomainConfig> findByDomain(String domain) {
        String normalizedDomain = DomainName.normalize(domain);
        TypedQuery<DomainConfigEntity> query = entityManager.createQuery(
                "SELECT d FROM DomainConfigEntity d WHERE LOWER(d.domainName) = :domain",
                DomainConfigEntity.class
        );
        query.setParameter("domain", normalizedDomain);
        List<DomainConfigEntity> results = query.getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(mapper.toDomain(results.get(0)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DomainConfig> findAll() {
        TypedQuery<DomainConfigEntity> query = entityManager.createQuery(
                "SELECT d FROM DomainConfigEntity d ORDER BY d.domainName",
                DomainConfigEntity.class
        );
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DomainConfig> findAllActive() {
        TypedQuery<DomainConfigEntity> query = entityManager.createQuery(
                "SELECT d FROM DomainConfigEntity d WHERE d.active = true ORDER BY d.domainName",
                DomainConfigEntity.class
        );
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DomainConfig> findLocalDomains() {
        TypedQuery<DomainConfigEntity> query = entityManager.createQuery(
                "SELECT d FROM DomainConfigEntity d WHERE d.localDomain = true AND d.active = true ORDER BY d.domainName",
                DomainConfigEntity.class
        );
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DomainConfig> findRemoteDomains() {
        TypedQuery<DomainConfigEntity> query = entityManager.createQuery(
                "SELECT d FROM DomainConfigEntity d WHERE d.localDomain = false AND d.active = true ORDER BY d.domainName",
                DomainConfigEntity.class
        );
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return entityManager.createQuery(
                "SELECT COUNT(d) FROM DomainConfigEntity d",
                Long.class
        ).getSingleResult();
    }

    @Override
    public void deleteById(String id) {
        DomainConfigEntity entity = entityManager.find(DomainConfigEntity.class, id);
        if (entity != null) {
            String domain = entity.getDomainName();
            entityManager.remove(entity);
            domainEventPublisher.publishEvent(new DomainConfigDeleted(id, domain));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByDomain(String domain) {
        String normalizedDomain = DomainName.normalize(domain);
        return entityManager.createQuery(
                "SELECT COUNT(d) FROM DomainConfigEntity d WHERE LOWER(d.domainName) = :domain",
                Long.class
        ).setParameter("domain", normalizedDomain).getSingleResult() > 0;
    }
}
