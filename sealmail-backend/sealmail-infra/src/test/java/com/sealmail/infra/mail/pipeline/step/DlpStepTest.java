package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.dlp.DlpContentKind;
import com.sealmail.domain.dlp.DlpContentPart;
import com.sealmail.domain.dlp.DlpEvaluationResult;
import com.sealmail.domain.dlp.DlpMaskingStrategy;
import com.sealmail.domain.dlp.DlpMatch;
import com.sealmail.domain.dlp.DlpRule;
import com.sealmail.domain.dlp.DlpRuleType;
import com.sealmail.domain.dlp.DlpUbaRiskLevel;
import com.sealmail.domain.dlp.spi.DlpEvaluationPort;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.domain.shared.event.AuditEvent;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DlpStepTest {

    @Test
    void warnOnlyAllowsMailToContinue() {
        byte[] payload = mailPayload();
        DlpStep step = stepReturning(result(DispositionAction.WARN));

        var result = step.execute(MessageBuilder.withPayload(payload).build());

        assertArrayEquals(payload, result.getPayload());
    }

    @Test
    void mustEncryptAddsHeaderForLaterEncryptionStep() {
        byte[] payload = mailPayload();
        DlpStep step = stepReturning(result(DispositionAction.MUST_ENCRYPT));

        var result = step.execute(MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context(payload))
                .build());

        MailProcessingContext context = context(result);
        assertTrue(context.decision().mustEncrypt());
    }

    @Test
    void quarantineStopsMailAndMarksDlpQuarantine() {
        byte[] payload = mailPayload();
        DlpStep step = stepReturning(result(DispositionAction.QUARANTINE));

        var result = step.execute(MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context(payload))
                .build());

        MailProcessingContext context = context(result);
        assertTrue(context.decision().requiresQuarantine());
        assertEquals("POLICY_VIOLATION", context.decision().quarantine().reason());
        assertTrue(context.decision().quarantine().detail().startsWith("DLP QUARANTINE:"));
    }

    @Test
    void blockStopsMailAndMarksDlpBlock() {
        byte[] payload = mailPayload();
        DlpStep step = stepReturning(result(DispositionAction.BLOCK));

        var result = step.execute(MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context(payload))
                .build());

        MailProcessingContext context = context(result);
        assertTrue(context.decision().requiresQuarantine());
        assertEquals("POLICY_VIOLATION", context.decision().quarantine().reason());
        assertTrue(context.decision().quarantine().detail().startsWith("DLP BLOCK:"));
    }

    @Test
    void scannerExceptionRecordsExceptionMailInsteadOfAllowingDelivery() {
        byte[] payload = mailPayload();
        DlpEvaluationPort service = mock(DlpEvaluationPort.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        when(service.evaluate(any(), any(), any(Boolean.class))).thenThrow(new IllegalStateException("scanner down"));
        DlpStep step = new DlpStep(service, domainEventPublisher);

        MailProcessingException exception = assertThrows(MailProcessingException.class,
                () -> step.execute(MessageBuilder.withPayload(payload)
                        .setHeader(MailProcessingHeaders.CONTEXT, context(payload))
                        .build()));

        assertEquals(MailProcessingErrorType.DLP, exception.errorType());
        assertTrue(exception.getMessage().contains("scanner down"));
        assertEquals(MailRecordDisposition.EXCEPTION, exception.recordDisposition());
    }

    private DlpStep stepReturning(DlpEvaluationResult result) {
        DlpEvaluationPort service = mock(DlpEvaluationPort.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        when(service.evaluate(any(), any(), any(Boolean.class))).thenReturn(result);
        return new DlpStep(service, domainEventPublisher);
    }

    @Test
    void publishesAuditEventForViolations() {
        byte[] payload = mailPayload();
        DlpEvaluationPort service = mock(DlpEvaluationPort.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        when(service.evaluate(any(), any(), any(Boolean.class))).thenReturn(result(DispositionAction.WARN));
        DlpStep step = new DlpStep(service, domainEventPublisher);

        step.execute(MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context(payload))
                .build());

        verify(domainEventPublisher).publishEvent(argThat((AuditEvent event) ->
                AuditLogType.DLP_VIOLATION.name().equals(event.getEventType())
                        && "MAIL_PROCESSING".equals(event.getResourceType())
                        && "processing-1".equals(event.getResourceId())
                        && "DLP_WARN".equals(event.getAction())
        ));
    }

    private DlpEvaluationResult result(DispositionAction action) {
        DlpRule rule = new DlpRule(
                "rule-id",
                "rule",
                "rule matched",
                DlpRuleType.KEYWORD,
                "matched",
                null,
                List.of(),
                1,
                5,
                DlpMaskingStrategy.DEFAULT,
                100,
                action,
                5,
                true,
                null,
                null);
        DlpContentPart part = new DlpContentPart(
                "body",
                DlpContentKind.BODY_TEXT,
                null,
                "text/plain",
                7,
                "matched",
                false,
                List.of());
        DlpMatch match = new DlpMatch(rule, part, "matched", 0, 7);
        return new DlpEvaluationResult(
                "event-1",
                action,
                action,
                5,
                List.of(match),
                List.of(),
                List.of(),
                List.of("policy-1"),
                List.of("group-1"),
                false,
                1,
                DlpUbaRiskLevel.LOW,
                List.of(),
                false);
    }

    private byte[] mailPayload() {
        return "Subject: dlp\r\n\r\nbody\r\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    private MailProcessingContext context(byte[] payload) {
        return MailProcessingContext.create(new MailEnvelope(
                "msg-1@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                "helo",
                Instant.now(),
                payload
        )).withProcessingId("processing-1");
    }

    private static MailProcessingContext context(org.springframework.messaging.Message<?> message) {
        return (MailProcessingContext) message.getHeaders().get(MailProcessingHeaders.CONTEXT);
    }
}
