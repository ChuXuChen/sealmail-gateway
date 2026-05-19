package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.mailauth.AuthenticationResultSet;
import com.sealmail.domain.shared.model.EmailAddress;

import java.util.List;
import java.util.UUID;

public record MailProcessingContext(
        MailEnvelope envelope,
        String processingId,
        MailDirection direction,
        RoutingDecision routingDecision,
        CryptoProfile cryptoProfile,
        MailProcessingDecision decision,
        CertificateSelection certificateSelection,
        RelayProfile relayProfile,
        AuditTrace auditTrace,
        byte[] originalMailContent,
        String subject,
        String quarantineReleaseId,
        MailRecordDisposition recordDisposition,
        AuthenticationResultSet mailAuthResults,
        boolean smimeEncrypted,
        String smimeEncryptionSuite,
        List<EmailAddress> smimeEncryptedRecipients
) {

    public MailProcessingContext {
        if (envelope == null) {
            throw new IllegalArgumentException("Mail envelope cannot be null");
        }
        cryptoProfile = cryptoProfile != null ? cryptoProfile : CryptoProfile.AUTO;
        decision = decision != null ? decision : MailProcessingDecision.none();
        certificateSelection = certificateSelection != null ? certificateSelection : CertificateSelection.empty();
        mailAuthResults = mailAuthResults != null ? mailAuthResults : AuthenticationResultSet.empty();
        originalMailContent = originalMailContent != null ? originalMailContent.clone() : envelope.getRawContent();
        smimeEncryptedRecipients = smimeEncryptedRecipients != null ? List.copyOf(smimeEncryptedRecipients) : List.of();
    }

    public static MailProcessingContext create(MailEnvelope envelope) {
        return new MailProcessingContext(
                envelope,
                UUID.randomUUID().toString(),
                null,
                null,
                CryptoProfile.AUTO,
                MailProcessingDecision.none(),
                CertificateSelection.empty(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null
        );
    }

    public static MailProcessingContext initial(MailEnvelope envelope,
                                                MailDirection direction,
                                                String submissionType,
                                                byte[] originalMailContent,
                                                String subject,
                                                String remoteAddress) {
        String messageId = envelope.getMessageId();
        String correlationId = messageId != null && !messageId.isBlank()
                ? messageId
                : UUID.randomUUID().toString();
        return create(envelope)
                .withDirection(direction)
                .withOriginalMailContent(originalMailContent)
                .withSubject(subject)
                .withAuditTrace(new AuditTrace(null, correlationId, messageId, submissionType, remoteAddress))
                .syncAuditTraceProcessingId();
    }

    private MailProcessingContext syncAuditTraceProcessingId() {
        if (auditTrace == null) {
            return this;
        }
        return withAuditTrace(new AuditTrace(
                processingId,
                auditTrace.correlationId(),
                auditTrace.messageId(),
                auditTrace.submissionType(),
                auditTrace.remoteAddress()));
    }

    public MailProcessingContext withProcessingId(String value) {
        MailProcessingContext context = new MailProcessingContext(envelope, value, direction, routingDecision,
                cryptoProfile, decision, certificateSelection, relayProfile, auditTrace, originalMailContent,
                subject, quarantineReleaseId, recordDisposition, mailAuthResults, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
        return context.syncAuditTraceProcessingId();
    }

    public MailProcessingContext withDirection(MailDirection value) {
        return new MailProcessingContext(envelope, processingId, value, routingDecision,
                cryptoProfile, decision, certificateSelection, relayProfile, auditTrace, originalMailContent,
                subject, quarantineReleaseId, recordDisposition, mailAuthResults, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
    }

    public MailProcessingContext withRoutingDecision(RoutingDecision value) {
        return new MailProcessingContext(envelope, processingId, direction, value,
                cryptoProfile, decision, certificateSelection, relayProfile, auditTrace, originalMailContent,
                subject, quarantineReleaseId, recordDisposition, mailAuthResults, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
    }

    public MailProcessingContext withCryptoProfile(CryptoProfile value) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision,
                value, decision, certificateSelection, relayProfile, auditTrace, originalMailContent, subject,
                quarantineReleaseId, recordDisposition, mailAuthResults, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
    }

    public MailProcessingContext withDecision(MailProcessingDecision value) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision,
                cryptoProfile, value, certificateSelection, relayProfile, auditTrace, originalMailContent, subject,
                quarantineReleaseId, recordDisposition, mailAuthResults, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
    }

    public MailProcessingContext withCertificateSelection(CertificateSelection value) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision,
                cryptoProfile, decision, value, relayProfile, auditTrace, originalMailContent, subject,
                quarantineReleaseId, recordDisposition, mailAuthResults, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
    }

    public MailProcessingContext withRelayProfile(RelayProfile value) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision,
                cryptoProfile, decision, certificateSelection, value, auditTrace, originalMailContent, subject,
                quarantineReleaseId, recordDisposition, mailAuthResults, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
    }

    public MailProcessingContext withAuditTrace(AuditTrace value) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision,
                cryptoProfile, decision, certificateSelection, relayProfile, value, originalMailContent, subject,
                quarantineReleaseId, recordDisposition, mailAuthResults, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
    }

    public MailProcessingContext withOriginalMailContent(byte[] value) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision,
                cryptoProfile, decision, certificateSelection, relayProfile, auditTrace, value, subject,
                quarantineReleaseId, recordDisposition, mailAuthResults, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
    }

    public MailProcessingContext withSubject(String value) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision,
                cryptoProfile, decision, certificateSelection, relayProfile, auditTrace, originalMailContent, value,
                quarantineReleaseId, recordDisposition, mailAuthResults, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
    }

    public MailProcessingContext withQuarantineReleaseId(String value) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision,
                cryptoProfile, decision, certificateSelection, relayProfile, auditTrace, originalMailContent, subject,
                value, recordDisposition, mailAuthResults, smimeEncrypted, smimeEncryptionSuite, smimeEncryptedRecipients);
    }

    public MailProcessingContext withRecordDisposition(MailRecordDisposition value) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision,
                cryptoProfile, decision, certificateSelection, relayProfile, auditTrace, originalMailContent, subject,
                quarantineReleaseId, value, mailAuthResults, smimeEncrypted, smimeEncryptionSuite, smimeEncryptedRecipients);
    }

    public MailProcessingContext withMailAuthResults(AuthenticationResultSet value) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision,
                cryptoProfile, decision, certificateSelection, relayProfile, auditTrace, originalMailContent, subject,
                quarantineReleaseId, recordDisposition, value, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
    }

    public MailProcessingContext withSmimeEncryption(String suite, List<EmailAddress> recipients) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision,
                cryptoProfile, decision, certificateSelection, relayProfile, auditTrace, originalMailContent, subject,
                quarantineReleaseId, recordDisposition, mailAuthResults, true, suite, recipients);
    }

    public MailContentSnapshot contentSnapshot() {
        return new MailContentSnapshot(envelope, originalMailContent, subject, smimeEncrypted, smimeEncryptionSuite);
    }

    public MailSecurityContext securityContext() {
        return new MailSecurityContext(
                cryptoProfile,
                certificateSelection,
                decision,
                mailAuthResults,
                smimeEncryptedRecipients);
    }

    public MailDeliveryContext deliveryContext() {
        return new MailDeliveryContext(
                direction,
                routingDecision,
                relayProfile,
                recordDisposition,
                quarantineReleaseId);
    }

    public MailTraceContext traceContext() {
        return new MailTraceContext(processingId, auditTrace);
    }

    @Override
    public byte[] originalMailContent() {
        return originalMailContent != null ? originalMailContent.clone() : new byte[0];
    }
}
