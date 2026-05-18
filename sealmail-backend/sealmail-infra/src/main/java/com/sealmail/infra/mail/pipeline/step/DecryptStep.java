package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.key.KeyManagementPort;
import com.sealmail.domain.key.KeyOperationResult;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.event.MailDecrypted;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.mail.pipeline.MailProcessingAuditEvents;
import com.sealmail.infra.mail.pipeline.MailProcessingMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Inbound pipeline step: Decrypt S/MIME encrypted mail.
 */
@Component
public class DecryptStep {

    private static final Logger log = LoggerFactory.getLogger(DecryptStep.class);

    private final SMIMEOperations smimeOperations;
    private final KeyManagementPort keyManagementPort;
    private final DomainEventPublisher domainEventPublisher;

    public DecryptStep(SMIMEOperations smimeOperations,
                       KeyManagementPort keyManagementPort,
                       DomainEventPublisher domainEventPublisher) {
        this.smimeOperations = smimeOperations;
        this.keyManagementPort = keyManagementPort;
        this.domainEventPublisher = domainEventPublisher;
    }

    public Message<byte[]> execute(Message<byte[]> message) {
        MailProcessingContext context = context(message);
        MailEnvelope envelope = context != null ? context.envelope() : null;
        if (envelope == null) {
            throw new MailProcessingException(
                    MailProcessingErrorType.DECRYPTION,
                    "Mail processing context not found in message headers",
                    context);
        }

        try {
            if (!smimeOperations.isEncrypted(message.getPayload())) {
                return message;
            }

            String recipientCert = context.certificateSelection().recipientCertificatePem();
            String thumbprint = context.certificateSelection().recipientCertificateThumbprint();

            if (recipientCert == null || thumbprint == null || thumbprint.isBlank()
                    || keyManagementPort.findActiveKeyForCertificate(thumbprint).isEmpty()) {
                throw new MailProcessingException(
                        MailProcessingErrorType.DECRYPTION,
                        missingKeyMessage(envelope),
                        context);
            }

            KeyOperationResult decryptResult =
                    keyManagementPort.decryptSmimeForCertificate(thumbprint, message.getPayload(), recipientCert);
            byte[] decrypted = decryptResult.payload();
            EmailAddress recipient = envelope.getRecipients().isEmpty() ? null : envelope.getRecipients().getFirst();
            if (recipient != null) {
                domainEventPublisher.publishEvent(new MailDecrypted(envelope.getMessageId(), recipient));
            }
            recordAudit(context, "SMIME_DECRYPT",
                    "recipientCertificateThumbprint=" + thumbprint
                            + ", keyId=" + decryptResult.keyRecord().getKeyId()
                            + ", ownerEmail=" + decryptResult.keyRecord().getOwner().getValue(),
                    true);
            return MailProcessingMessages.withPayload(message, decrypted);

        } catch (Exception e) {
            if (e instanceof MailProcessingException mailProcessingException) {
                recordAudit(
                        mailProcessingException.context() != null ? mailProcessingException.context() : context,
                        "SMIME_DECRYPT_FAILED",
                        "errorType=" + mailProcessingException.errorType().name()
                                + MailProcessingAuditEvents.detailPresence(mailProcessingException.getMessage()),
                        false);
                throw mailProcessingException;
            }
            recordAudit(context, "SMIME_DECRYPT_FAILED",
                    "errorType=" + MailProcessingErrorType.DECRYPTION
                            + MailProcessingAuditEvents.detailPresence(e.getMessage()), false);
            throw new MailProcessingException(
                    MailProcessingErrorType.DECRYPTION,
                    "Decryption failed: " + e.getMessage(),
                    context,
                    e);
        }
    }

    private void recordAudit(MailProcessingContext context, String action, String detail, boolean success) {
        MailProcessingAuditEvents.publish(
                domainEventPublisher,
                AuditLogType.EMAIL_DECRYPTED,
                context,
                action,
                detail,
                success);
    }

    public String getStepName() {
        return "decrypt";
    }

    private MailProcessingContext context(Message<?> message) {
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }

    private String missingKeyMessage(MailEnvelope envelope) {
        String recipients = envelope.getRecipients().stream()
                .map(EmailAddress::getValue)
                .collect(java.util.stream.Collectors.joining(", "));
        return "Encrypted S/MIME mail cannot be decrypted: missing managed key for recipient(s): " + recipients;
    }
}
