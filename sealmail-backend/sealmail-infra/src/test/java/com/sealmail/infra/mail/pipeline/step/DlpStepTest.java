package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.dlp.DlpScanResult;
import com.sealmail.domain.dlp.DlpViolation;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.domain.shared.event.AuditEvent;
import com.sealmail.infra.dlp.DlpService;
import com.sealmail.infra.dlp.MimeContentExtractor;
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
        DlpService service = mock(DlpService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        when(service.scan(any(), any(), any(), any())).thenThrow(new IllegalStateException("scanner down"));
        DlpStep step = new DlpStep(service, new MimeContentExtractor(), domainEventPublisher);

        MailProcessingException exception = assertThrows(MailProcessingException.class,
                () -> step.execute(MessageBuilder.withPayload(payload)
                        .setHeader(MailProcessingHeaders.CONTEXT, context(payload))
                        .build()));

        assertEquals(MailProcessingErrorType.DLP, exception.errorType());
        assertTrue(exception.getMessage().contains("scanner down"));
        assertEquals(MailRecordDisposition.EXCEPTION, exception.recordDisposition());
    }

    private DlpStep stepReturning(DlpScanResult result) {
        DlpService service = mock(DlpService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        when(service.scan(any(), any(), any(), any())).thenReturn(result);
        return new DlpStep(service, new MimeContentExtractor(), domainEventPublisher);
    }

    @Test
    void publishesAuditEventForViolations() {
        byte[] payload = mailPayload();
        DlpService service = mock(DlpService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        when(service.scan(any(), any(), any(), any())).thenReturn(result(DispositionAction.WARN));
        DlpStep step = new DlpStep(service, new MimeContentExtractor(), domainEventPublisher);

        step.execute(MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context(payload))
                .build());

        verify(domainEventPublisher).publishEvent(argThat((AuditEvent event) ->
                AuditLogType.DLP_VIOLATION.name().equals(event.getEventType())
                        && "EMAIL".equals(event.getResourceType())
                        && context(payload).envelope().getMessageId().equals(event.getResourceId())
                        && "DLP_WARN".equals(event.getAction())
        ));
    }

    private DlpScanResult result(DispositionAction action) {
        return new DlpScanResult("test", List.of(new DlpViolation(
                "rule",
                "rule matched",
                "matched",
                5,
                action
        )), 1);
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
        ));
    }

    private static MailProcessingContext context(org.springframework.messaging.Message<?> message) {
        return (MailProcessingContext) message.getHeaders().get(MailProcessingHeaders.CONTEXT);
    }
}
