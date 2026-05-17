package com.sealmail.infra.config;

import com.sealmail.domain.config.QuarantinePolicyPort;
import com.sealmail.domain.policy.event.QuarantinePolicyChanged;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.QuarantinePolicyEntity;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@Transactional
public class QuarantinePolicyService implements QuarantinePolicyPort {

    private static final String DEFAULT_ID = "default";

    private final EntityManager entityManager;
    private final DomainEventPublisher domainEventPublisher;
    private final TransactionTemplate initializationTransaction;

    public QuarantinePolicyService(EntityManager entityManager,
                                   DomainEventPublisher domainEventPublisher,
                                   PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.domainEventPublisher = domainEventPublisher;
        this.initializationTransaction = new TransactionTemplate(transactionManager);
        this.initializationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional(readOnly = true)
    public QuarantinePolicySettings getSettings() {
        return toSettings(entity());
    }

    @Override
    public QuarantinePolicySettings updateSettings(QuarantinePolicySettingsUpdate update) {
        QuarantinePolicyEntity entity = entity();
        applyUpdate(entity, update);
        entity.setUpdatedAt(Instant.now());
        QuarantinePolicyEntity saved = entityManager.merge(entity);
        domainEventPublisher.publishEvent(new QuarantinePolicyChanged(DEFAULT_ID, changedFields(update)));
        return toSettings(saved);
    }

    private QuarantinePolicyEntity entity() {
        QuarantinePolicyEntity entity = entityManager.find(QuarantinePolicyEntity.class, DEFAULT_ID);
        if (entity != null) {
            return entity;
        }
        return initializationTransaction.execute(status -> {
            QuarantinePolicyEntity existing = entityManager.find(QuarantinePolicyEntity.class, DEFAULT_ID);
            if (existing != null) {
                return existing;
            }
            QuarantinePolicyEntity created = defaultEntity();
            entityManager.persist(created);
            entityManager.flush();
            return created;
        });
    }

    private QuarantinePolicyEntity defaultEntity() {
        Instant now = Instant.now();
        QuarantinePolicyEntity entity = new QuarantinePolicyEntity();
        entity.setId(DEFAULT_ID);
        entity.setMaxRetentionDays(30);
        entity.setNotificationEnabled(false);
        entity.setReleaseRequiresEncryption(false);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private void applyUpdate(QuarantinePolicyEntity entity, QuarantinePolicySettingsUpdate update) {
        if (update == null) {
            return;
        }
        if (update.maxRetentionDays() != null) {
            require(update.maxRetentionDays() > 0, "Quarantine retention days must be positive");
            entity.setMaxRetentionDays(update.maxRetentionDays());
        }
        if (update.notificationEnabled() != null) {
            entity.setNotificationEnabled(update.notificationEnabled());
        }
        if (update.releaseRequiresEncryption() != null) {
            entity.setReleaseRequiresEncryption(update.releaseRequiresEncryption());
        }
    }

    private QuarantinePolicySettings toSettings(QuarantinePolicyEntity entity) {
        return new QuarantinePolicySettings(
                entity.getMaxRetentionDays(),
                entity.isNotificationEnabled(),
                entity.isReleaseRequiresEncryption(),
                entity.getUpdatedAt());
    }

    private List<String> changedFields(QuarantinePolicySettingsUpdate update) {
        if (update == null) {
            return List.of("GENERAL");
        }
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        if (update.maxRetentionDays() != null) {
            fields.add("retention");
        }
        if (update.notificationEnabled() != null) {
            fields.add("notification");
        }
        if (update.releaseRequiresEncryption() != null) {
            fields.add("release");
        }
        return fields.isEmpty() ? List.of("GENERAL") : List.copyOf(fields);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
