package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.event.MailVerified;
import com.sealmail.infra.mail.pipeline.MailPipelineStep;
import com.sealmail.infra.mail.pipeline.PipelineResult;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Inbound pipeline step: Verify S/MIME signature.
 */
@Component
public class VerifyStep implements MailPipelineStep {

    private final SMIMEOperations smimeOperations;

    public VerifyStep(SMIMEOperations smimeOperations) {
        this.smimeOperations = smimeOperations;
    }

    @Override
    public PipelineResult execute(Message<byte[]> message) {
        MailEnvelope envelope = (MailEnvelope) message.getHeaders().get("mailEnvelope");
        if (envelope == null) {
            return PipelineResult.failure("Mail envelope not found in message headers");
        }

        try {
            if (!smimeOperations.isSigned(message.getPayload())) {
                return PipelineResult.success(message.getPayload());
            }

            String senderCert = (String) message.getHeaders().get("senderCertificate");

            if (senderCert == null) {
                // Unknown sender certificate - skip verification instead of quarantining blindly.
                return PipelineResult.success(message.getPayload());
            }

            boolean valid = smimeOperations.verifySignature(message.getPayload(), senderCert);
            if (!valid) {
                return PipelineResult.quarantine(
                        message.getPayload(),
                        "SIGNATURE_INVALID",
                        "Invalid S/MIME signature from: " + envelope.getSender());
            }

            byte[] extracted = smimeOperations.extractSignedContent(message.getPayload());
            return PipelineResult.success(extracted, new MailVerified(
                    envelope.getMessageId(),
                    envelope.getSender(),
                    true));

        } catch (Exception e) {
            return PipelineResult.quarantine(
                    message.getPayload(),
                    "SIGNATURE_INVALID",
                    "Signature verification failed: " + e.getMessage());
        }
    }

    @Override
    public String getStepName() {
        return "verify-signature";
    }
}
