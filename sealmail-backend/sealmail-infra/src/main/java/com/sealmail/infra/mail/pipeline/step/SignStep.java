package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.key.KeyManagementPort;
import com.sealmail.domain.key.KeyOperationResult;
import com.sealmail.domain.mailsecurity.CryptoProfile;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.event.MailSigned;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.mail.pipeline.MailProcessingAuditEvents;
import com.sealmail.infra.mail.pipeline.MailProcessingMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Outbound pipeline step: Sign outgoing mail with S/MIME.
 */
@Component
public class SignStep {

    private static final Logger log = LoggerFactory.getLogger(SignStep.class);

    private final KeyManagementPort keyManagementPort;
    private final DomainEventPublisher domainEventPublisher;

    public SignStep(KeyManagementPort keyManagementPort,
                    DomainEventPublisher domainEventPublisher) {
        this.keyManagementPort = keyManagementPort;
        this.domainEventPublisher = domainEventPublisher;
    }

    public Message<byte[]> execute(Message<byte[]> message) {
        MailProcessingContext context = context(message);
        MailEnvelope envelope = context != null ? context.envelope() : null;
        if (envelope == null) {
            throw new MailProcessingException(
                    MailProcessingErrorType.SIGNING,
                    "Mail processing context not found in message headers",
                    context);
        }

        if (!context.decision().signingRequired()) {
            return message;
        }

        try {
            String senderCert = context.certificateSelection().senderCertificatePem();
            String thumbprint = context.certificateSelection().senderCertificateThumbprint();
            CryptoProfile profile = context.cryptoProfile();

            if (senderCert == null) {
                throw new MailProcessingException(
                        MailProcessingErrorType.SIGNING,
                        signingMaterialMissingMessage(profile, "未找到匹配签名证书"),
                        context);
            }

            if (thumbprint == null || thumbprint.isBlank()
                    || keyManagementPort.findActiveKeyForCertificate(thumbprint).isEmpty()) {
                throw new MailProcessingException(
                        MailProcessingErrorType.SIGNING,
                        signingMaterialMissingMessage(profile, "已选择签名证书但未找到对应私钥"),
                        context);
            }

            byte[] original = message.getPayload();
            KeyOperationResult signResult =
                    keyManagementPort.signSmimeForCertificate(thumbprint, original, senderCert);
            byte[] signed = signResult.payload();

            log.info("=== S/MIME SIGNING COMPLETED ===");
            log.info("  Original size: {} bytes", original.length);
            log.info("  Signed size: {} bytes", signed.length);
            recordAudit(context, "SMIME_SIGN", "profile=" + profile
                    + ", senderCertificateThumbprint=" + thumbprint
                    + ", keyId=" + signResult.keyRecord().getKeyId()
                    + ", ownerEmail=" + signResult.keyRecord().getOwner().getValue(), true);

            if (thumbprint != null && !thumbprint.isBlank()) {
                domainEventPublisher.publishEvent(new MailSigned(
                        envelope.getMessageId(),
                        envelope.getSender(),
                        new com.sealmail.domain.certificate.CertificateId(thumbprint)));
                return MailProcessingMessages.withPayload(message, signed);
            }
            return MailProcessingMessages.withPayload(message, signed);

        } catch (Exception e) {
            if (e instanceof MailProcessingException mailProcessingException) {
                recordAudit(
                        mailProcessingException.context() != null ? mailProcessingException.context() : context,
                        "SMIME_SIGN_FAILED",
                        "errorType=" + mailProcessingException.errorType().name()
                                + MailProcessingAuditEvents.detailPresence(mailProcessingException.getMessage()),
                        false);
                throw mailProcessingException;
            }
            log.error("S/MIME signing failed: {}", e.getMessage(), e);
            recordAudit(context, "SMIME_SIGN_FAILED",
                    "errorType=" + MailProcessingErrorType.SIGNING
                            + MailProcessingAuditEvents.detailPresence(e.getMessage()), false);
            throw new MailProcessingException(
                    MailProcessingErrorType.SIGNING,
                    "S/MIME signing failed: " + e.getMessage(),
                    context,
                    e);
        }
    }

    private void recordAudit(MailProcessingContext context, String action, String detail, boolean success) {
        MailProcessingAuditEvents.publish(
                domainEventPublisher,
                AuditLogType.EMAIL_SIGNED,
                context,
                action,
                detail,
                success);
    }

    public String getStepName() {
        return "sign";
    }

    private MailProcessingContext context(Message<?> message) {
        return MailProcessingMessages.context(message);
    }

    private String signingMaterialMissingMessage(CryptoProfile profile, String detail) {
        if (profile != null && profile.isConcrete()) {
            return profile + " profile 策略：" + detail;
        }
        return detail;
    }
}
