package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingDecision;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailErrorClassifierTest {

    private final MailErrorClassifier classifier = new MailErrorClassifier();

    @Test
    void releaseRoutingQuarantineUsesContextQuarantineReasonAndFailsProcessing() {
        MailProcessingContext context = context("raw".getBytes())
                .withQuarantineReleaseId("q-1")
                .withDecision(MailProcessingDecision.none()
                        .withQuarantine("DOMAIN_NOT_CONFIGURED", "domain disabled"));

        MailErrorClassification classification = classify(new MailProcessingException(
                MailProcessingErrorType.ROUTING,
                "Quarantine release stopped: domain disabled",
                context));

        assertEquals(MailProcessingErrorType.ROUTING, classification.errorType());
        assertEquals(MailErrorTarget.QUARANTINE, classification.target());
        assertEquals(ProcessingResult.FAILED, classification.processingResult());
        assertEquals("DOMAIN_NOT_CONFIGURED", classification.quarantineReason());
        assertEquals("domain disabled", classification.quarantineDetail());
        assertEquals("MAIL_DEAD_LETTER", classification.auditAction());
    }

    @Test
    void certificateMissingEncryptionFailureMapsToEncryptionFailedQuarantine() {
        MailProcessingContext context = context("raw".getBytes());

        MailErrorClassification classification = classify(new MailProcessingException(
                MailProcessingErrorType.ENCRYPTION,
                "以下收件人没有加密证书: recipient@example.com",
                context));

        assertEquals(MailProcessingErrorType.ENCRYPTION, classification.errorType());
        assertEquals(MailErrorTarget.QUARANTINE, classification.target());
        assertEquals("ENCRYPTION_FAILED", classification.quarantineReason());
        assertTrue(classification.quarantineDetail().contains("errorType=ENCRYPTION"));
        assertTrue(classification.quarantineDetail().contains("没有加密证书"));
        assertEquals(MailRecordDisposition.EXCEPTION, classification.recordDisposition());
    }

    @Test
    void dlpFailureMapsToScanErrorQuarantine() {
        MailProcessingContext context = context("raw".getBytes());

        MailErrorClassification classification = classify(new MailProcessingException(
                MailProcessingErrorType.DLP,
                "DLP scan failed: scanner down",
                context));

        assertEquals(MailProcessingErrorType.DLP, classification.errorType());
        assertEquals(MailErrorTarget.QUARANTINE, classification.target());
        assertEquals("SCAN_ERROR", classification.quarantineReason());
        assertTrue(classification.quarantineDetail().contains("scanner down"));
        assertEquals(ProcessingResult.FAILED, classification.processingResult());
    }

    @Test
    void retryableSmtpRelayFailureRecordsExceptionResultAndRetryAuditAction() {
        MailProcessingContext context = context("raw".getBytes());

        MailErrorClassification classification = classify(new MailProcessingException(
                MailProcessingErrorType.RELAY,
                "Mail relay failed: smtp down",
                context,
                MailRecordDisposition.EXCEPTION,
                true,
                null));

        assertEquals(MailProcessingErrorType.RELAY, classification.errorType());
        assertEquals(MailErrorTarget.QUARANTINE, classification.target());
        assertEquals(ProcessingResult.EXCEPTION, classification.processingResult());
        assertEquals("MAIL_RETRY", classification.auditAction());
        assertEquals("POLICY_VIOLATION", classification.quarantineReason());
        assertTrue(classification.quarantineDetail().contains("smtp down"));
    }

    @Test
    void quarantinePersistenceFailureGoesStraightToDeadLetter() {
        MailProcessingContext context = context("raw".getBytes());

        MailErrorClassification classification = classify(new MailProcessingException(
                MailProcessingErrorType.QUARANTINE,
                "db down",
                context));

        assertEquals(MailProcessingErrorType.QUARANTINE, classification.errorType());
        assertEquals(MailErrorTarget.DEAD_LETTER, classification.target());
        assertEquals(ProcessingResult.FAILED, classification.processingResult());
        assertEquals("POLICY_VIOLATION", classification.quarantineReason());
    }

    @Test
    void messagingExceptionUsesFailedMessageContextWhenPayloadHasNoContext() {
        MailProcessingContext context = context("raw".getBytes());
        Message<byte[]> failedMessage = message("raw".getBytes(), context);

        MailErrorClassification classification = classifier.classify(new ErrorMessage(
                new MessagingException(failedMessage, "handler exploded")));

        assertEquals(MailProcessingErrorType.UNKNOWN, classification.errorType());
        assertSame(context, classification.context());
        assertEquals(MailErrorTarget.QUARANTINE, classification.target());
        assertEquals("POLICY_VIOLATION", classification.quarantineReason());
    }

    private MailErrorClassification classify(MailProcessingException error) {
        MailProcessingContext context = error.context();
        return classifier.classify(new ErrorMessage(error, message(context.originalMailContent(), context)));
    }

    private static Message<byte[]> message(byte[] payload, MailProcessingContext context) {
        return MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }

    private static MailProcessingContext context(byte[] payload) {
        return MailProcessingContext.initial(
                new MailEnvelope(
                        "msg-1@example.com",
                        new EmailAddress("sender@example.com"),
                        List.of(new EmailAddress("recipient@example.com")),
                        "127.0.0.1",
                        "helo",
                        Instant.now(),
                        payload),
                MailDirection.OUTBOUND,
                "smtp_submission",
                payload,
                "subject",
                "127.0.0.1");
    }
}
