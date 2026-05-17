package com.sealmail.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.config.GmEdgePolicyPort;
import com.sealmail.domain.policy.event.GmEdgePolicyChanged;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.GmEdgePolicyEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GmEdgePolicyServiceTest {

    @Test
    void getSettingsCreatesDisabledDefaultPolicy() throws Exception {
        EntityManager entityManager = mock(EntityManager.class);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        GmEdgePolicyService service = service(entityManager, eventPublisher);

        GmEdgePolicyPort.GmEdgePolicySettings settings = service.getSettings();

        assertFalse(settings.enabled());
        assertEquals(2525, settings.inbound().startTlsPort());
        assertEquals(2465, settings.inbound().implicitTlsPort());
        assertEquals(2526, settings.outbound().smartHostPort());
        assertEquals(2530, settings.postfix().port());
        assertEquals(List.of("TLCPv1.1", "TLCP", "TLSv1.3"), settings.tls().protocols());
        verify(entityManager).persist(org.mockito.ArgumentMatchers.any(GmEdgePolicyEntity.class));
        verify(entityManager).flush();
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateSettingsStoresRoutesAndPublishesAuditEvent() throws Exception {
        EntityManager entityManager = mock(EntityManager.class);
        GmEdgePolicyEntity entity = entity();
        when(entityManager.find(GmEdgePolicyEntity.class, "default")).thenReturn(entity);
        when(entityManager.merge(entity)).thenReturn(entity);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        GmEdgePolicyService service = service(entityManager, eventPublisher);

        GmEdgePolicyPort.GmEdgePolicySettings settings = service.updateSettings(
                new GmEdgePolicyPort.GmEdgePolicySettingsUpdate(
                        true,
                        null,
                        null,
                        null,
                        new GmEdgePolicyPort.TlsSettingsUpdate(
                                List.of("TLCP", "TLSv1.3"),
                                List.of("TLS_SM4_GCM_SM3"),
                                "/run/secrets/edge.p12",
                                "env:KEY_PASS",
                                false,
                                "PKCS12",
                                "/run/secrets/trust.p12",
                                "env:TRUST_PASS",
                                false,
                                "PKCS12",
                                false),
                        null,
                        List.of(new GmEdgePolicyPort.RouteSettings(
                                "*.partner.example.cn",
                                "gm.partner.example.cn",
                                2525,
                                "STARTTLS"))));

        assertTrue(settings.enabled());
        assertEquals(1, settings.routes().size());
        assertEquals("*.partner.example.cn", settings.routes().get(0).domainPattern());
        assertEquals("env:KEY_PASS", settings.tls().keyStorePasswordSecretRef());
        ArgumentCaptor<GmEdgePolicyChanged> eventCaptor = ArgumentCaptor.forClass(GmEdgePolicyChanged.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertTrue(eventCaptor.getValue().getChangedFields().contains("routes"));
        assertTrue(eventCaptor.getValue().getChangedFields().contains("tls"));
    }

    @Test
    void rejectsUnsupportedStandardTlsProtocol() throws Exception {
        EntityManager entityManager = mock(EntityManager.class);
        GmEdgePolicyEntity entity = entity();
        when(entityManager.find(GmEdgePolicyEntity.class, "default")).thenReturn(entity);
        GmEdgePolicyService service = service(entityManager, mock(DomainEventPublisher.class));

        GmEdgePolicyPort.GmEdgePolicySettingsUpdate update = new GmEdgePolicyPort.GmEdgePolicySettingsUpdate(
                null,
                null,
                null,
                null,
                new GmEdgePolicyPort.TlsSettingsUpdate(
                        List.of("TLSv1.2"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                null,
                null);

        assertThrows(IllegalArgumentException.class, () -> service.updateSettings(update));
    }

    private static GmEdgePolicyService service(EntityManager entityManager,
                                               DomainEventPublisher eventPublisher) throws Exception {
        GmEdgePolicyService service = new GmEdgePolicyService(
                eventPublisher,
                new ObjectMapper(),
                new NoopTransactionManager());
        Field field = GmEdgePolicyService.class.getDeclaredField("entityManager");
        field.setAccessible(true);
        field.set(service, entityManager);
        return service;
    }

    private static GmEdgePolicyEntity entity() {
        Instant now = Instant.now();
        GmEdgePolicyEntity entity = new GmEdgePolicyEntity();
        entity.setId("default");
        entity.setEnabled(false);
        entity.setInboundEnabled(true);
        entity.setInboundBindAddress("0.0.0.0");
        entity.setInboundStartTlsPort(2525);
        entity.setInboundImplicitTlsPort(2465);
        entity.setInboundBacklog(128);
        entity.setInboundMaxConnections(1024);
        entity.setOutboundEnabled(true);
        entity.setOutboundBindAddress("127.0.0.1");
        entity.setOutboundSmartHostPort(2526);
        entity.setOutboundBacklog(128);
        entity.setOutboundMaxConnections(512);
        entity.setPostfixHost("127.0.0.1");
        entity.setPostfixPort(2530);
        entity.setTlsProtocols("TLCPv1.1,TLCP,TLSv1.3");
        entity.setTlsCipherSuites("TLS_SM4_GCM_SM3,TLS_SM4_CCM_SM3");
        entity.setTlsKeyStoreType("PKCS12");
        entity.setTlsTrustStoreType("PKCS12");
        entity.setConnectTimeoutMs(10000);
        entity.setReadTimeoutMs(60000);
        entity.setMaxMessageSizeBytes(52428800);
        entity.setMaxLineLengthBytes(16384);
        entity.setMaxRecipients(100);
        entity.setRoutesJson("[]");
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
