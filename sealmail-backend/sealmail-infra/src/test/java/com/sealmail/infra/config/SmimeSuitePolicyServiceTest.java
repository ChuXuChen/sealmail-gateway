package com.sealmail.infra.config;

import com.sealmail.domain.config.SmimeSuitePolicyPort;
import com.sealmail.domain.policy.event.SmimeSuitePolicyChanged;
import com.sealmail.infra.config.properties.SmimeCryptoProperties;
import com.sealmail.infra.crypto.SmimeAlgorithmSuites;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.SmimeSuitePolicyEntity;
import jakarta.persistence.EntityManager;
import org.bouncycastle.cms.CMSAlgorithm;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmimeSuitePolicyServiceTest {

    @Test
    void getSettingsCreatesYamlBackedDefaultEntity() throws Exception {
        EntityManager entityManager = mock(EntityManager.class);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        SmimeCryptoProperties properties = new SmimeCryptoProperties();
        properties.setDefaultStandardSuite(SmimeAlgorithmSuites.STANDARD_AES_128_CBC);
        SmimeSuitePolicyService service = service(entityManager, eventPublisher, properties);

        SmimeSuitePolicyPort.SmimeSuitePolicySettings settings = service.getSettings();

        assertEquals(SmimeAlgorithmSuites.STANDARD_AES_128_CBC, settings.defaultStandardSuite());
        assertEquals(SmimeAlgorithmSuites.GM_SM4_CBC, settings.defaultGmSuite());
        assertEquals(4, settings.standardSuites().size());
        assertEquals(2, settings.gmSuites().size());
        verify(entityManager).persist(org.mockito.ArgumentMatchers.any(SmimeSuitePolicyEntity.class));
        verify(entityManager).flush();
    }

    @Test
    void updateSettingsChangesDefaultSuiteAndPublishesAuditEvent() throws Exception {
        EntityManager entityManager = mock(EntityManager.class);
        SmimeSuitePolicyEntity entity = entity();
        when(entityManager.find(SmimeSuitePolicyEntity.class, "default")).thenReturn(entity);
        when(entityManager.merge(entity)).thenReturn(entity);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        SmimeSuitePolicyService service = service(entityManager, eventPublisher, new SmimeCryptoProperties());

        SmimeSuitePolicyPort.SmimeSuitePolicySettings settings = service.updateSettings(
                new SmimeSuitePolicyPort.SmimeSuitePolicySettingsUpdate(
                        SmimeAlgorithmSuites.STANDARD_AES_256_GCM,
                        SmimeAlgorithmSuites.GM_SM4_CBC));

        assertEquals(SmimeAlgorithmSuites.STANDARD_AES_256_GCM, settings.defaultStandardSuite());
        assertEquals(CMSAlgorithm.AES256_GCM,
                service.effectiveSuites().get(com.sealmail.domain.mailsecurity.CryptoProfile.STANDARD)
                        .contentEncryptionAlgorithm());
        ArgumentCaptor<SmimeSuitePolicyChanged> eventCaptor = ArgumentCaptor.forClass(SmimeSuitePolicyChanged.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("default", eventCaptor.getValue().getConfigId());
        assertEquals("defaultStandardSuite", eventCaptor.getValue().getChangedFields().get(0));
    }

    @Test
    void rejectsDefaultSuiteFromWrongProfile() throws Exception {
        EntityManager entityManager = mock(EntityManager.class);
        SmimeSuitePolicyEntity entity = entity();
        when(entityManager.find(SmimeSuitePolicyEntity.class, "default")).thenReturn(entity);
        SmimeSuitePolicyService service = service(entityManager, mock(DomainEventPublisher.class), new SmimeCryptoProperties());

        assertThrows(IllegalArgumentException.class, () -> service.updateSettings(
                new SmimeSuitePolicyPort.SmimeSuitePolicySettingsUpdate(
                        SmimeAlgorithmSuites.GM_SM4_CBC,
                        null)));
    }

    private static SmimeSuitePolicyService service(EntityManager entityManager,
                                                   DomainEventPublisher eventPublisher,
                                                   SmimeCryptoProperties properties) throws Exception {
        SmimeSuitePolicyService service = new SmimeSuitePolicyService(
                entityManager,
                eventPublisher,
                properties,
                new NoopTransactionManager());
        return service;
    }

    private static SmimeSuitePolicyEntity entity() {
        Instant now = Instant.now();
        SmimeSuitePolicyEntity entity = new SmimeSuitePolicyEntity();
        entity.setId("default");
        entity.setDefaultStandardSuite(SmimeAlgorithmSuites.STANDARD_AES_256_CBC);
        entity.setDefaultGmSuite(SmimeAlgorithmSuites.GM_SM4_CBC);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
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
