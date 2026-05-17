package com.sealmail.infra.config;

import com.sealmail.domain.config.QuarantinePolicyPort;
import com.sealmail.domain.policy.event.QuarantinePolicyChanged;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.QuarantinePolicyEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuarantinePolicyServiceTest {

    @Test
    void getSettingsCreatesSafeDefaultEntity() throws Exception {
        EntityManager entityManager = mock(EntityManager.class);
        QuarantinePolicyService service = service(entityManager, mock(DomainEventPublisher.class));

        QuarantinePolicyPort.QuarantinePolicySettings settings = service.getSettings();

        assertEquals(30, settings.maxRetentionDays());
        assertFalse(settings.notificationEnabled());
        assertFalse(settings.releaseRequiresEncryption());
        verify(entityManager).persist(org.mockito.ArgumentMatchers.any(QuarantinePolicyEntity.class));
        verify(entityManager).flush();
    }

    @Test
    void updateSettingsPersistsRuntimePolicyAndPublishesAuditEvent() throws Exception {
        EntityManager entityManager = mock(EntityManager.class);
        QuarantinePolicyEntity entity = entity();
        when(entityManager.find(QuarantinePolicyEntity.class, "default")).thenReturn(entity);
        when(entityManager.merge(entity)).thenReturn(entity);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        QuarantinePolicyService service = service(entityManager, eventPublisher);

        service.updateSettings(new QuarantinePolicyPort.QuarantinePolicySettingsUpdate(
                45,
                true,
                true
        ));

        assertEquals(45, entity.getMaxRetentionDays());
        assertTrue(entity.isNotificationEnabled());
        assertTrue(entity.isReleaseRequiresEncryption());
        ArgumentCaptor<QuarantinePolicyChanged> eventCaptor = ArgumentCaptor.forClass(QuarantinePolicyChanged.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("default", eventCaptor.getValue().getConfigId());
        assertTrue(eventCaptor.getValue().getChangedFields().contains("retention"));
        assertTrue(eventCaptor.getValue().getChangedFields().contains("notification"));
        assertTrue(eventCaptor.getValue().getChangedFields().contains("release"));
    }

    private static QuarantinePolicyService service(EntityManager entityManager,
                                                   DomainEventPublisher eventPublisher) throws Exception {
        return new QuarantinePolicyService(entityManager, eventPublisher, new NoopTransactionManager());
    }

    private static QuarantinePolicyEntity entity() {
        QuarantinePolicyEntity entity = new QuarantinePolicyEntity();
        entity.setId("default");
        entity.setMaxRetentionDays(30);
        entity.setNotificationEnabled(false);
        entity.setReleaseRequiresEncryption(false);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private static class NoopTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
