package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.dlp.DlpContentBundle;
import com.sealmail.domain.mailsecurity.AttachmentSecurityAction;
import com.sealmail.domain.mailsecurity.AttachmentSecurityFinding;
import com.sealmail.domain.mailsecurity.AttachmentSecurityResult;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailInspectionBundle;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingDecision;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.mail.inspection.MailInspectionService;
import com.sealmail.infra.mail.pipeline.AttachmentSecurityService;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.mail.pipeline.MailProcessingMessages;
import com.sealmail.infra.mail.pipeline.MailProcessingStatusService;
import com.sealmail.infra.mail.pipeline.UnifiedMailDecisionService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.anyString;

class AttachmentSecurityStepTest {

    @Test
    void allowResultReusesInspectionBundleAndLeavesDeliveryOpen() {
        MailInspectionService inspectionService = mock(MailInspectionService.class);
        AttachmentSecurityService attachmentSecurityService = mock(AttachmentSecurityService.class);
        UnifiedMailDecisionService decisionService = mock(UnifiedMailDecisionService.class);
        MailProcessingStatusService statusService = mock(MailProcessingStatusService.class);
        AttachmentSecurityStep step = new AttachmentSecurityStep(
                inspectionService,
                attachmentSecurityService,
                decisionService,
                statusService);

        byte[] rawMail = "raw".getBytes();
        MailProcessingContext context = context(rawMail);
        MailInspectionBundle bundle = new MailInspectionBundle(new DlpContentBundle(List.of(), List.of()), List.of(), List.of());
        AttachmentSecurityResult result = new AttachmentSecurityResult(
                "PASS",
                AttachmentSecurityAction.ALLOW,
                0,
                0,
                0L,
                List.of(),
                List.of("No issues"));
        when(inspectionService.inspect(eq(rawMail), eq(context))).thenReturn(bundle);
        when(attachmentSecurityService.evaluate(bundle)).thenReturn(result);

        Message<byte[]> output = step.execute(message(rawMail, context));

        assertArrayEquals(rawMail, output.getPayload());
        MailProcessingContext outputContext = MailProcessingMessages.context(output);
        assertSame(bundle, outputContext.inspectionBundle());
        assertEquals(context.processingId(), outputContext.processingId());
        verify(statusService).recordAttachmentSecurity(any(MailProcessingContext.class), eq(result), anyString());
        verify(decisionService, never()).applyAttachmentSecurity(
                any(MailProcessingContext.class),
                any(AttachmentSecurityResult.class),
                anyString());
    }

    @Test
    void quarantineResultDelegatesToUnifiedDecisionService() {
        MailInspectionService inspectionService = mock(MailInspectionService.class);
        AttachmentSecurityService attachmentSecurityService = mock(AttachmentSecurityService.class);
        UnifiedMailDecisionService decisionService = mock(UnifiedMailDecisionService.class);
        MailProcessingStatusService statusService = mock(MailProcessingStatusService.class);
        AttachmentSecurityStep step = new AttachmentSecurityStep(
                inspectionService,
                attachmentSecurityService,
                decisionService,
                statusService);

        byte[] rawMail = "raw".getBytes();
        MailProcessingContext context = context(rawMail);
        MailInspectionBundle bundle = new MailInspectionBundle(new DlpContentBundle(List.of(), List.of()), List.of(), List.of());
        AttachmentSecurityFinding finding = new AttachmentSecurityFinding(
                "HIGH_RISK_EXTENSION",
                95,
                AttachmentSecurityAction.QUARANTINE,
                "Blocked extension detected",
                "invoice.pdf.exe",
                "exe",
                "application/pdf",
                "application/x-msdownload",
                false,
                false,
                List.of("invoice.pdf.exe"));
        AttachmentSecurityResult result = new AttachmentSecurityResult(
                "QUARANTINED",
                AttachmentSecurityAction.QUARANTINE,
                95,
                1,
                512L,
                List.of(finding),
                List.of());
        when(inspectionService.inspect(eq(rawMail), eq(context))).thenReturn(bundle);
        when(attachmentSecurityService.evaluate(bundle)).thenReturn(result);
        when(decisionService.applyAttachmentSecurity(
                any(MailProcessingContext.class),
                eq(result),
                anyString())).thenAnswer(invocation -> {
            MailProcessingContext enriched = invocation.getArgument(0);
            return enriched.withDecision(MailProcessingDecision.none().withQuarantine(
                    "POLICY_VIOLATION",
                    "attachment blocked"))
                    .withRecordDisposition(MailRecordDisposition.EXCEPTION);
        });

        Message<byte[]> output = step.execute(message(rawMail, context));

        MailProcessingContext outputContext = MailProcessingMessages.context(output);
        assertTrue(outputContext.decision().requiresQuarantine());
        assertEquals("POLICY_VIOLATION", outputContext.decision().quarantine().reason());
        assertEquals("attachment blocked", outputContext.decision().quarantine().detail());
        verify(statusService).recordAttachmentSecurity(any(MailProcessingContext.class), eq(result), anyString());
        verify(decisionService).applyAttachmentSecurity(
                any(MailProcessingContext.class),
                eq(result),
                anyString());
    }

    private static MailProcessingContext context(byte[] rawMail) {
        MailEnvelope envelope = new MailEnvelope(
                "msg-1@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                "helo",
                Instant.now(),
                rawMail);
        return MailProcessingContext.create(envelope)
                .withProcessingId("processing-1")
                .withDirection(MailDirection.OUTBOUND);
    }

    private static Message<byte[]> message(byte[] payload, MailProcessingContext context) {
        return MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }
}
