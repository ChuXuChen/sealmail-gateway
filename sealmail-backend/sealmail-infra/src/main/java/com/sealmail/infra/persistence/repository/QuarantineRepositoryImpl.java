package com.sealmail.infra.persistence.repository;

import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.quarantine.QuarantineRepository;
import com.sealmail.domain.quarantine.QuarantineStatus;
import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.QuarantinedMailEntity;
import com.sealmail.infra.persistence.mapper.QuarantinedMailMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class QuarantineRepositoryImpl implements QuarantineRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private final QuarantinedMailMapper mapper;
    private final DomainEventPublisher domainEventPublisher;

    public QuarantineRepositoryImpl(QuarantinedMailMapper mapper, DomainEventPublisher domainEventPublisher) {
        this.mapper = mapper;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    public QuarantinedMail save(QuarantinedMail quarantinedMail) {
        QuarantinedMailEntity entity = mapper.toEntity(quarantinedMail);
        QuarantinedMailEntity existing = entityManager.find(QuarantinedMailEntity.class, entity.getId());
        if (existing != null) {
            entity.setCreatedAt(existing.getCreatedAt());
            entity.setVersion(existing.getVersion());
            entityManager.merge(entity);
        } else {
            entityManager.persist(entity);
        }
        domainEventPublisher.publishEvents(quarantinedMail);
        return quarantinedMail;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QuarantinedMail> findById(String id) {
        QuarantinedMailEntity entity = entityManager.find(QuarantinedMailEntity.class, id);
        return Optional.ofNullable(entity).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuarantinedMail> findByStatus(QuarantineStatus status) {
        TypedQuery<QuarantinedMailEntity> query = entityManager.createQuery(
                "SELECT q FROM QuarantinedMailEntity q WHERE q.status = :status",
                QuarantinedMailEntity.class
        );
        query.setParameter("status", status.name());
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuarantinedMail> findByMessageId(String messageId) {
        TypedQuery<QuarantinedMailEntity> query = entityManager.createQuery(
                "SELECT q FROM QuarantinedMailEntity q WHERE q.messageId = :messageId",
                QuarantinedMailEntity.class
        );
        query.setParameter("messageId", messageId);
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuarantinedMail> findAll(int offset, int limit) {
        TypedQuery<QuarantinedMailEntity> query = entityManager.createQuery(
                "SELECT q FROM QuarantinedMailEntity q ORDER BY q.createdAt DESC",
                QuarantinedMailEntity.class
        );
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuarantinedMail> findByReason(QuarantineReason reason, int offset, int limit) {
        TypedQuery<QuarantinedMailEntity> query = entityManager.createQuery(
                "SELECT q FROM QuarantinedMailEntity q WHERE q.reason = :reason ORDER BY q.createdAt DESC",
                QuarantinedMailEntity.class
        );
        query.setParameter("reason", reason.name());
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(String id) {
        QuarantinedMailEntity entity = entityManager.find(QuarantinedMailEntity.class, id);
        if (entity != null) {
            entityManager.remove(entity);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return entityManager.createQuery(
                "SELECT COUNT(q) FROM QuarantinedMailEntity q", Long.class
        ).getSingleResult();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(QuarantineStatus status) {
        return entityManager.createQuery(
                "SELECT COUNT(q) FROM QuarantinedMailEntity q WHERE q.status = :status", Long.class
        ).setParameter("status", status.name()).getSingleResult();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByReason(QuarantineReason reason) {
        return entityManager.createQuery(
                "SELECT COUNT(q) FROM QuarantinedMailEntity q WHERE q.reason = :reason", Long.class
        ).setParameter("reason", reason.name()).getSingleResult();
    }

}
