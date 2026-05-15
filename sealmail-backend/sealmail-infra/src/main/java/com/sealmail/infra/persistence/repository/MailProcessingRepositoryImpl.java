package com.sealmail.infra.persistence.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.mailsecurity.MailProcessing;
import com.sealmail.domain.mailsecurity.MailProcessingRepository;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.domain.mailsecurity.ProcessingStep;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.MailProcessingEntity;
import com.sealmail.infra.persistence.mapper.MailProcessingMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class MailProcessingRepositoryImpl implements MailProcessingRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private final MailProcessingMapper mapper;
    private final ObjectMapper objectMapper;
    private final DomainEventPublisher domainEventPublisher;

    public MailProcessingRepositoryImpl(MailProcessingMapper mapper,
                                        ObjectMapper objectMapper,
                                        DomainEventPublisher domainEventPublisher) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    public MailProcessing save(MailProcessing mailProcessing) {
        MailProcessingEntity entity = new MailProcessingEntity();
        entity.setId(mailProcessing.getId());
        entity.setMessageId(mailProcessing.getEnvelope().getMessageId());
        entity.setSenderEmail(mailProcessing.getEnvelope().getSender().getValue());
        entity.setRecipients(serializeRecipients(mailProcessing.getEnvelope().getRecipients()));
        entity.setRemoteHost(mailProcessing.getEnvelope().getRemoteHost());
        entity.setHelo(mailProcessing.getEnvelope().getHelo());
        entity.setReceivedAt(mailProcessing.getEnvelope().getReceivedAt());
        entity.setDirection(mailProcessing.getDirection().name());
        entity.setResult(mailProcessing.getResult() != null ? mailProcessing.getResult().name() : null);
        entity.setUpdatedAt(Instant.now());

        // 检查是否存在
        MailProcessingEntity existing = entityManager.find(MailProcessingEntity.class, mailProcessing.getId());
        if (existing == null) {
            entity.setCreatedAt(Instant.now());
            entityManager.persist(entity);
        } else {
            entity.setCreatedAt(existing.getCreatedAt());
            entity.setVersion(existing.getVersion());
            entityManager.detach(existing);
            entityManager.merge(entity);
        }

        domainEventPublisher.publishEvents(mailProcessing);
        return mailProcessing;
    }

    private String serializeRecipients(List<EmailAddress> recipients) {
        try {
            List<String> emails = recipients.stream().map(EmailAddress::getValue).toList();
            return objectMapper.writeValueAsString(emails);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize recipients", e);
        }
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Optional<MailProcessing> findById(String id) {
        MailProcessingEntity entity = entityManager.find(MailProcessingEntity.class, id);
        return Optional.ofNullable(entity).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MailProcessing> findByMessageId(String messageId) {
        TypedQuery<MailProcessingEntity> query = entityManager.createQuery(
                "SELECT m FROM MailProcessingEntity m WHERE m.messageId = :messageId",
                MailProcessingEntity.class
        );
        query.setParameter("messageId", messageId);
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MailProcessing> findByResult(ProcessingResult result) {
        TypedQuery<MailProcessingEntity> query = entityManager.createQuery(
                "SELECT m FROM MailProcessingEntity m WHERE m.result = :result",
                MailProcessingEntity.class
        );
        query.setParameter("result", result.name());
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }
}
