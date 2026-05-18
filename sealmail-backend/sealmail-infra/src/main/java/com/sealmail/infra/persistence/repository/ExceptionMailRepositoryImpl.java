package com.sealmail.infra.persistence.repository;

import com.sealmail.domain.exceptionmail.ExceptionMail;
import com.sealmail.domain.exceptionmail.ExceptionMailRepository;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.ExceptionMailEntity;
import com.sealmail.infra.persistence.entity.MailRawContentEntity;
import com.sealmail.infra.persistence.mapper.ExceptionMailMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public class ExceptionMailRepositoryImpl implements ExceptionMailRepository {

    private final EntityManager entityManager;
    private final ExceptionMailMapper mapper;
    private final DomainEventPublisher domainEventPublisher;

    public ExceptionMailRepositoryImpl(EntityManager entityManager,
                                       ExceptionMailMapper mapper,
                                       DomainEventPublisher domainEventPublisher) {
        this.entityManager = entityManager;
        this.mapper = mapper;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    public ExceptionMail save(ExceptionMail exceptionMail) {
        ExceptionMailEntity entity = mapper.toEntity(exceptionMail);
        ExceptionMailEntity existing = entityManager.find(ExceptionMailEntity.class, entity.getId());
        String rawContentId = existing != null ? existing.getRawContentId() : null;
        if (rawContentId == null && hasRawContent(exceptionMail.getRawContent())) {
            rawContentId = persistRawContent(exceptionMail.getRawContent());
        }
        entity.setRawContentId(rawContentId);
        if (existing != null) {
            entity.setCreatedAt(existing.getCreatedAt());
            entity.setVersion(existing.getVersion());
            entityManager.merge(entity);
        } else {
            entityManager.persist(entity);
        }
        domainEventPublisher.publishEvents(exceptionMail);
        return exceptionMail;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExceptionMail> findById(String id) {
        ExceptionMailEntity entity = entityManager.find(ExceptionMailEntity.class, id);
        return Optional.ofNullable(entity)
                .map(value -> mapper.toDomain(value, loadRawContent(value.getRawContentId())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExceptionMail> findByMessageId(String messageId) {
        TypedQuery<ExceptionMailEntity> query = entityManager.createQuery(
                "SELECT e FROM ExceptionMailEntity e WHERE e.messageId = :messageId ORDER BY e.createdAt DESC",
                ExceptionMailEntity.class
        );
        query.setParameter("messageId", messageId);
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExceptionMail> findAll(int offset, int limit) {
        TypedQuery<ExceptionMailEntity> query = entityManager.createQuery(
                "SELECT e FROM ExceptionMailEntity e ORDER BY e.createdAt DESC",
                ExceptionMailEntity.class
        );
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExceptionMail> findByReason(QuarantineReason reason, int offset, int limit) {
        TypedQuery<ExceptionMailEntity> query = entityManager.createQuery(
                "SELECT e FROM ExceptionMailEntity e WHERE e.reason = :reason ORDER BY e.createdAt DESC",
                ExceptionMailEntity.class
        );
        query.setParameter("reason", reason.name());
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return entityManager.createQuery(
                "SELECT COUNT(e) FROM ExceptionMailEntity e", Long.class
        ).getSingleResult();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByReason(QuarantineReason reason) {
        return entityManager.createQuery(
                "SELECT COUNT(e) FROM ExceptionMailEntity e WHERE e.reason = :reason", Long.class
        ).setParameter("reason", reason.name()).getSingleResult();
    }

    private boolean hasRawContent(byte[] rawContent) {
        return rawContent != null && rawContent.length > 0;
    }

    private String persistRawContent(byte[] rawContent) {
        MailRawContentEntity raw = new MailRawContentEntity();
        raw.setId(UUID.randomUUID().toString());
        raw.setContent(Base64.getEncoder().encodeToString(rawContent));
        raw.setSha256(sha256(rawContent));
        raw.setSizeBytes(rawContent.length);
        raw.setContentType("message/rfc822;base64");
        raw.setCreatedAt(Instant.now());
        entityManager.persist(raw);
        return raw.getId();
    }

    private byte[] loadRawContent(String rawContentId) {
        if (rawContentId == null || rawContentId.isBlank()) {
            return new byte[0];
        }
        MailRawContentEntity raw = entityManager.find(MailRawContentEntity.class, rawContentId);
        if (raw == null || raw.getContent() == null || raw.getContent().isBlank()) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(raw.getContent());
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is not available", e);
        }
    }
}
