package com.sealmail.infra.persistence.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.shared.event.DomainEvent;
import com.sealmail.domain.shared.repository.DomainEventRepository;
import com.sealmail.infra.persistence.entity.DomainEventEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class DomainEventRepositoryImpl implements DomainEventRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper;

    public DomainEventRepositoryImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(DomainEvent event) {
        DomainEventEntity entity = new DomainEventEntity();
        entity.setEventId(event.getEventId());
        entity.setAggregateId(extractAggregateId(event));
        entity.setAggregateType(event.getClass().getEnclosingClass() != null
                ? event.getClass().getEnclosingClass().getSimpleName()
                : "DomainEvent");
        entity.setEventType(event.getClass().getSimpleName());
        entity.setOccurredAt(event.getOccurredAt());
        try {
            entity.setEventData(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize domain event", e);
        }
        entityManager.persist(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DomainEvent> findByAggregateId(String aggregateId) {
        TypedQuery<DomainEventEntity> query = entityManager.createQuery(
                "SELECT e FROM DomainEventEntity e WHERE e.aggregateId = :id ORDER BY e.occurredAt",
                DomainEventEntity.class
        );
        query.setParameter("id", aggregateId);
        return query.getResultList().stream()
                .map(this::deserializeEvent)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DomainEvent> findAll() {
        TypedQuery<DomainEventEntity> query = entityManager.createQuery(
                "SELECT e FROM DomainEventEntity e ORDER BY e.occurredAt",
                DomainEventEntity.class
        );
        return query.getResultList().stream()
                .map(this::deserializeEvent)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private String extractAggregateId(DomainEvent event) {
        try {
            for (var field : event.getClass().getDeclaredFields()) {
                if (field.getName().contains("id") || field.getName().contains("Id")) {
                    field.setAccessible(true);
                    var value = field.get(event);
                    if (value != null) {
                        return value.toString();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    private Optional<DomainEvent> deserializeEvent(DomainEventEntity entity) {
        try {
            String className = resolveEventClassName(entity);
            Class<?> clazz = Class.forName(className);
            return Optional.of((DomainEvent) objectMapper.readValue(entity.getEventData(), clazz));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String resolveEventClassName(DomainEventEntity entity) {
        String basePackage = "com.sealmail.domain.";
        String subPackage = switch (entity.getAggregateType()) {
            case "Certificate" -> "certificate.event.";
            case "DomainConfig" -> "policy.event.";
            case "QuarantinedMail" -> "quarantine.event.";
            case "User" -> "identity.event.";
            case "MailProcessing" -> "mailsecurity.event.";
            default -> "shared.event.";
        };
        return basePackage + subPackage + entity.getEventType();
    }
}
