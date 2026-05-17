package com.sealmail.infra.config;

import com.sealmail.domain.config.RelayPolicyPort;
import com.sealmail.domain.config.SecretReferenceResolver;
import com.sealmail.domain.mailsecurity.SmtpTransportSecurity;
import com.sealmail.domain.policy.event.RelayPolicyChanged;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.RelayPolicyEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelayPolicyServiceTest {

    @Test
    void getSettingsCreatesDisabledDefaultEntityWithoutDeploymentFallbackOrSecretResolution() throws Exception {
        EntityManager entityManager = mock(EntityManager.class);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        SecretReferenceResolver secretResolver = mock(SecretReferenceResolver.class);
        RelayPolicyService service = service(entityManager, eventPublisher, secretResolver);

        RelayPolicyPort.RelayPolicySettings settings = service.getSettings();

        assertFalse(settings.enabled());
        assertEquals("localhost", settings.host());
        assertEquals(25, settings.port());
        assertFalse(settings.useTls());
        assertEquals(SmtpTransportSecurity.NONE, settings.transportSecurity());
        assertNull(settings.username());
        assertFalse(settings.passwordConfigured());
        assertNull(settings.passwordSecretRef());
        assertEquals(30000, settings.timeoutMs());
        assertNull(settings.envelopeFrom());
        verify(entityManager).persist(org.mockito.ArgumentMatchers.any(RelayPolicyEntity.class));
        verify(entityManager).flush();
        verify(secretResolver, never()).resolve(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getProbeSettingsResolvesPasswordSecretForInternalMailTestOnly() throws Exception {
        EntityManager entityManager = mock(EntityManager.class);
        RelayPolicyEntity entity = entity();
        entity.setPasswordSecretRef("env:SMTP_PASSWORD");
        when(entityManager.find(RelayPolicyEntity.class, "default")).thenReturn(entity);
        SecretReferenceResolver secretResolver = mock(SecretReferenceResolver.class);
        when(secretResolver.resolve("env:SMTP_PASSWORD")).thenReturn("resolved-password");
        RelayPolicyService service = service(entityManager, mock(DomainEventPublisher.class), secretResolver);

        RelayPolicyPort.RelayProbeSettings settings = service.getProbeSettings();

        assertEquals("resolved-password", settings.password());
        verify(secretResolver).resolve("env:SMTP_PASSWORD");
    }

    @Test
    void updateSettingsStoresSecretReferenceAndPublishesAuditEvent() throws Exception {
        EntityManager entityManager = mock(EntityManager.class);
        RelayPolicyEntity entity = entity();
        when(entityManager.find(RelayPolicyEntity.class, "default")).thenReturn(entity);
        when(entityManager.merge(entity)).thenReturn(entity);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        RelayPolicyService service = service(entityManager, eventPublisher, mock(SecretReferenceResolver.class));

        service.updateSettings(new RelayPolicyPort.RelayPolicySettingsUpdate(
                true,
                "smtp2.example.net",
                465,
                true,
                null,
                "new-user",
                "env:NEW_SMTP_PASSWORD",
                false,
                20000,
                "new-bounce@example.net"
        ));

        assertEquals("smtp2.example.net", entity.getHost());
        assertEquals(465, entity.getPort());
        assertEquals(SmtpTransportSecurity.SMTPS, entity.getTransportSecurity());
        assertEquals("new-user", entity.getUsername());
        assertEquals("env:NEW_SMTP_PASSWORD", entity.getPasswordSecretRef());
        ArgumentCaptor<RelayPolicyChanged> eventCaptor = ArgumentCaptor.forClass(RelayPolicyChanged.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("default", eventCaptor.getValue().getConfigId());
        assertTrue(eventCaptor.getValue().getChangedFields().contains("connection"));
        assertTrue(eventCaptor.getValue().getChangedFields().contains("authentication"));
    }

    private static RelayPolicyService service(EntityManager entityManager,
                                              DomainEventPublisher eventPublisher,
                                              SecretReferenceResolver secretResolver) throws Exception {
        RelayPolicyService service = new RelayPolicyService(
                eventPublisher,
                secretResolver,
                new NoopTransactionManager());
        Field field = RelayPolicyService.class.getDeclaredField("entityManager");
        field.setAccessible(true);
        field.set(service, entityManager);
        return service;
    }

    private static RelayPolicyEntity entity() {
        RelayPolicyEntity entity = new RelayPolicyEntity();
        entity.setId("default");
        entity.setEnabled(true);
        entity.setHost("smtp.example.net");
        entity.setPort(587);
        entity.setUseTls(true);
        entity.setTransportSecurity(SmtpTransportSecurity.STARTTLS);
        entity.setUsername("relay-user");
        entity.setPasswordSecretRef(null);
        entity.setTimeoutMs(15000);
        entity.setEnvelopeFrom("bounce@example.net");
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
