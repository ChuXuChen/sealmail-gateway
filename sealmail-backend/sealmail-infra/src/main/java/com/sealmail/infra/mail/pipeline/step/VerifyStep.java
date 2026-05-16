package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.mailsecurity.event.MailVerified;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.mail.pipeline.MailProcessingMessages;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Inbound pipeline step: Verify S/MIME signature.
 */
@Component
public class VerifyStep {

    private final SMIMEOperations smimeOperations;
    private final DomainEventPublisher domainEventPublisher;

    public VerifyStep(SMIMEOperations smimeOperations,
                      DomainEventPublisher domainEventPublisher) {
        this.smimeOperations = smimeOperations;
        this.domainEventPublisher = domainEventPublisher;
    }

    public Message<byte[]> execute(Message<byte[]> message) {
        MailProcessingContext context = context(message);
        MailEnvelope envelope = context != null ? context.envelope() : null;
        if (envelope == null) {
            throw new MailProcessingException(
                    MailProcessingErrorType.VERIFICATION,
                    "Mail processing context not found in message headers",
                    context);
        }

        try {
            if (!smimeOperations.isSigned(message.getPayload())) {
                return message;
            }

            String senderCert = context.certificateSelection().senderCertificatePem();

            if (senderCert == null) {
                // Unknown sender certificate - skip verification instead of quarantining blindly.
                return message;
            }

            boolean valid = smimeOperations.verifySignature(message.getPayload(), senderCert);
            if (!valid) {
                return MailProcessingMessages.quarantine(
                        message,
                        "SIGNATURE_INVALID",
                        "Invalid S/MIME signature from: " + envelope.getSender(),
                        MailRecordDisposition.EXCEPTION);
            }

            byte[] extracted = smimeOperations.extractSignedContent(message.getPayload());
            domainEventPublisher.publishEvent(new MailVerified(
                    envelope.getMessageId(),
                    envelope.getSender(),
                    true));
            return MailProcessingMessages.withPayload(message, extracted);

        } catch (Exception e) {
            throw new MailProcessingException(
                    MailProcessingErrorType.VERIFICATION,
                    "Signature verification failed: " + e.getMessage(),
                    context,
                    e);
        }
    }

    public String getStepName() {
        return "verify-signature";
    }

    private MailProcessingContext context(Message<?> message) {
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }
}
