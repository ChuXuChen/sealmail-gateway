package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.policy.PreferredAlgorithm;
import com.sealmail.domain.shared.model.EmailAddress;

import java.util.List;

public record MailProcessingContext(
        MailEnvelope envelope,
        String processingId,
        MailDirection direction,
        RoutingDecision routingDecision,
        PreferredAlgorithm preferredAlgorithm,
        CryptoProfile cryptoProfile,
        MailProcessingDecision decision,
        CertificateSelection certificateSelection,
        RelayProfile relayProfile,
        AuditTrace auditTrace,
        byte[] originalMailContent,
        String subject,
        String quarantineReleaseId,
        MailRecordDisposition recordDisposition,
        boolean smimeEncrypted,
        String smimeEncryptionSuite,
        List<EmailAddress> smimeEncryptedRecipients
) {

    public MailProcessingContext {
        if (envelope == null) {
            throw new IllegalArgumentException("Mail envelope cannot be null");
        }
        preferredAlgorithm = preferredAlgorithm != null ? preferredAlgorithm : PreferredAlgorithm.AUTO;
        cryptoProfile = cryptoProfile != null ? cryptoProfile : CryptoProfile.fromPreferredAlgorithm(preferredAlgorithm);
        decision = decision != null ? decision : MailProcessingDecision.none();
        certificateSelection = certificateSelection != null ? certificateSelection : CertificateSelection.empty();
        originalMailContent = originalMailContent != null ? originalMailContent.clone() : envelope.getRawContent();
        smimeEncryptedRecipients = smimeEncryptedRecipients != null ? List.copyOf(smimeEncryptedRecipients) : List.of();
    }

    public static MailProcessingContext create(MailEnvelope envelope) {
        return new MailProcessingContext(
                envelope,
                null,
                null,
                null,
                PreferredAlgorithm.AUTO,
                CryptoProfile.AUTO,
                MailProcessingDecision.none(),
                CertificateSelection.empty(),
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
        return create(envelope)
                .withDirection(direction)
                .withOriginalMailContent(originalMailContent)
                .withSubject(subject)
                .withAuditTrace(new AuditTrace(null, messageId, messageId, submissionType, remoteAddress));
    }

    public MailProcessingContext withProcessingId(String value) {
        return new MailProcessingContext(envelope, value, direction, routingDecision, preferredAlgorithm,
                cryptoProfile, decision, certificateSelection, relayProfile, auditTrace, originalMailContent,
                subject, quarantineReleaseId, recordDisposition, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
    }

    public MailProcessingContext withDirection(MailDirection value) {
        return new MailProcessingContext(envelope, processingId, value, routingDecision, preferredAlgorithm,
                cryptoProfile, decision, certificateSelection, relayProfile, auditTrace, originalMailContent,
                subject, quarantineReleaseId, recordDisposition, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
    }

    public MailProcessingContext withRoutingDecision(RoutingDecision value) {
        return new MailProcessingContext(envelope, processingId, direction, value, preferredAlgorithm,
                cryptoProfile, decision, certificateSelection, relayProfile, auditTrace, originalMailContent,
                subject, quarantineReleaseId, recordDisposition, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
    }

    public MailProcessingContext withPreferredAlgorithm(PreferredAlgorithm value) {
        PreferredAlgorithm algorithm = value != null ? value : PreferredAlgorithm.AUTO;
        return new MailProcessingContext(envelope, processingId, direction, routingDecision, algorithm,
                CryptoProfile.fromPreferredAlgorithm(algorithm), decision, certificateSelection, relayProfile,
                auditTrace, originalMailContent, subject, quarantineReleaseId, recordDisposition, smimeEncrypted,
                smimeEncryptionSuite, smimeEncryptedRecipients);
    }

    public MailProcessingContext withCryptoProfile(CryptoProfile value) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision, preferredAlgorithm,
                value, decision, certificateSelection, relayProfile, auditTrace, originalMailContent, subject,
                quarantineReleaseId, recordDisposition, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
    }

    public MailProcessingContext withDecision(MailProcessingDecision value) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision, preferredAlgorithm,
                cryptoProfile, value, certificateSelection, relayProfile, auditTrace, originalMailContent, subject,
                quarantineReleaseId, recordDisposition, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
    }

    public MailProcessingContext withCertificateSelection(CertificateSelection value) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision, preferredAlgorithm,
                cryptoProfile, decision, value, relayProfile, auditTrace, originalMailContent, subject,
                quarantineReleaseId, recordDisposition, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
    }

    public MailProcessingContext withRelayProfile(RelayProfile value) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision, preferredAlgorithm,
                cryptoProfile, decision, certificateSelection, value, auditTrace, originalMailContent, subject,
                quarantineReleaseId, recordDisposition, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
    }

    public MailProcessingContext withAuditTrace(AuditTrace value) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision, preferredAlgorithm,
                cryptoProfile, decision, certificateSelection, relayProfile, value, originalMailContent, subject,
                quarantineReleaseId, recordDisposition, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
    }

    public MailProcessingContext withOriginalMailContent(byte[] value) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision, preferredAlgorithm,
                cryptoProfile, decision, certificateSelection, relayProfile, auditTrace, value, subject,
                quarantineReleaseId, recordDisposition, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
    }

    public MailProcessingContext withSubject(String value) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision, preferredAlgorithm,
                cryptoProfile, decision, certificateSelection, relayProfile, auditTrace, originalMailContent, value,
                quarantineReleaseId, recordDisposition, smimeEncrypted, smimeEncryptionSuite,
                smimeEncryptedRecipients);
    }

    public MailProcessingContext withQuarantineReleaseId(String value) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision, preferredAlgorithm,
                cryptoProfile, decision, certificateSelection, relayProfile, auditTrace, originalMailContent, subject,
                value, recordDisposition, smimeEncrypted, smimeEncryptionSuite, smimeEncryptedRecipients);
    }

    public MailProcessingContext withRecordDisposition(MailRecordDisposition value) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision, preferredAlgorithm,
                cryptoProfile, decision, certificateSelection, relayProfile, auditTrace, originalMailContent, subject,
                quarantineReleaseId, value, smimeEncrypted, smimeEncryptionSuite, smimeEncryptedRecipients);
    }

    public MailProcessingContext withSmimeEncryption(String suite, List<EmailAddress> recipients) {
        return new MailProcessingContext(envelope, processingId, direction, routingDecision, preferredAlgorithm,
                cryptoProfile, decision, certificateSelection, relayProfile, auditTrace, originalMailContent, subject,
                quarantineReleaseId, recordDisposition, true, suite, recipients);
    }

    @Override
    public byte[] originalMailContent() {
        return originalMailContent != null ? originalMailContent.clone() : new byte[0];
    }
}
