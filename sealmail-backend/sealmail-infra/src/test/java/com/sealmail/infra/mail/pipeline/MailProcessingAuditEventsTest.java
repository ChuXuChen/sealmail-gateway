package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.mailsecurity.AuditTrace;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.shared.event.AuditEvent;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.events.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MailProcessingAuditEventsTest {

    @Test
    void publishesTraceEventByProcessingIdWithoutSensitiveMaterial() {
        DomainEventPublisher publisher = mock(DomainEventPublisher.class);
        MailProcessingContext context = MailProcessingContext.create(new MailEnvelope(
                        "msg-1@example.com",
                        new EmailAddress("sender@example.com"),
                        List.of(new EmailAddress("recipient@example.com")),
                        "127.0.0.1",
                        "helo",
                        Instant.now(),
                        "Subject: secret\r\n\r\nbody".getBytes()))
                .withDirection(MailDirection.OUTBOUND)
                .withProcessingId("processing-1")
                .withAuditTrace(new AuditTrace(
                        "processing-1",
                        "correlation-1",
                        "msg-1@example.com",
                        "api",
                        "127.0.0.1"));

        MailProcessingAuditEvents.publish(
                publisher,
                AuditLogType.EMAIL_RELAYED,
                context,
                "SMTP_RELAY",
                "privateKey=should-not-appear, password=should-not-appear",
                true);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publishEvent(captor.capture());
        AuditEvent event = captor.getValue();

        assertEquals(AuditLogType.EMAIL_RELAYED.name(), event.getEventType());
        assertEquals(MailProcessingAuditEvents.RESOURCE_TYPE, event.getResourceType());
        assertEquals("processing-1", event.getResourceId());
        assertTrue(event.getDescription().contains("correlationId=correlation-1"));
        assertTrue(event.getDescription().contains("messageId=msg-1@example.com"));
        assertFalse(event.getDescription().contains("Subject: secret"));
        assertFalse(event.getDescription().contains("should-not-appear"));
    }

    @Test
    void omitsQuarantineDetailTextFromRouteDecisionSummary() {
        String summary = MailProcessingAuditEvents.routeDecisionSummary(
                new com.sealmail.domain.mailsecurity.RoutingDecision.Quarantine(
                        com.sealmail.domain.quarantine.QuarantineReason.POLICY_VIOLATION,
                        "customer body fragment 4111111111111111"));

        assertTrue(summary.contains("detailPresent=true"));
        assertFalse(summary.contains("customer body fragment"));
        assertFalse(summary.contains("4111111111111111"));
    }
}
