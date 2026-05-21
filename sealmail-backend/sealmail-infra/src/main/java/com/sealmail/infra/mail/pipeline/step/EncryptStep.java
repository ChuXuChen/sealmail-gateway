package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.mailsecurity.CryptoProfile;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.mailsecurity.event.MailEncrypted;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.mail.pipeline.MailProcessingAuditEvents;
import com.sealmail.infra.mail.pipeline.MailProcessingMessages;
import com.sealmail.infra.mail.pipeline.MailProcessingStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outbound pipeline step: Encrypt outgoing mail with S/MIME for each recipient.
 */
@Component
public class EncryptStep {

    private static final Logger log = LoggerFactory.getLogger(EncryptStep.class);

    private final SMIMEOperations smimeOperations;
    private final DomainEventPublisher domainEventPublisher;
    private final MailProcessingStatusService statusService;

    public EncryptStep(SMIMEOperations smimeOperations,
                       DomainEventPublisher domainEventPublisher,
                       MailProcessingStatusService statusService) {
        this.smimeOperations = smimeOperations;
        this.domainEventPublisher = domainEventPublisher;
        this.statusService = statusService;
    }

    public Message<byte[]> execute(Message<byte[]> message) {
        MailProcessingContext context = context(message);
        MailEnvelope envelope = context != null ? context.envelope() : null;
        if (envelope == null) {
            throw new MailProcessingException(
                    MailProcessingErrorType.ENCRYPTION,
                    "Mail processing context not found in message headers",
                    context);
        }

        boolean encryptionEnabled = context.decision().encryptionRequired();
        boolean mustEncrypt = context.decision().mustEncrypt();
        if (!encryptionEnabled && !mustEncrypt) {
            recordSkipped(context, "Encryption not required");
            return message;
        }

        try {
            EncryptionPlan plan = buildEncryptionPlan(context);
            if (!plan.success()) {
                throw new MailProcessingException(
                        MailProcessingErrorType.ENCRYPTION,
                        mustEncrypt ? "DLP MUST_ENCRYPT: " + plan.failureDetail() : plan.failureDetail(),
                        context,
                        MailRecordDisposition.EXCEPTION,
                        false,
                        null);
            }

            byte[] payload = message.getPayload();
            int originalSize = payload.length;
            List<String> certChain = plan.certificates().values().stream().toList();
            payload = smimeOperations.encryptMultiple(payload, certChain, plan.profile());
            List<MailEncrypted> events = encryptedEvents(envelope, plan.certificates(), plan.thumbprints());
            for (EmailAddress recipient : plan.certificates().keySet()) {
                log.info("  Encrypted for recipient: {} using {}", recipient, plan.profile());
            }

            log.info("=== S/MIME ENCRYPTION COMPLETED ===");
            log.info("  Original size: {} bytes", originalSize);
            log.info("  Encrypted size: {} bytes", payload.length);

            context = context
                    .withCryptoProfile(plan.profile())
                    .withSmimeEncryption(plan.profile().name(), List.copyOf(plan.certificates().keySet()));
            recordAudit(context, "SMIME_ENCRYPT", "profile=" + plan.profile()
                    + ", recipientCertificateCount=" + plan.certificates().size()
                    + ", recipientThumbprints=" + plan.thumbprints().values().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .collect(java.util.stream.Collectors.joining(",")), true);
            recordSuccess(context, plan);
            events.forEach(domainEventPublisher::publishEvent);
            return MailProcessingMessages.withPayloadAndContext(message, payload, context);

        } catch (Exception e) {
            if (e instanceof MailProcessingException mailProcessingException) {
                recordAudit(
                        mailProcessingException.context() != null ? mailProcessingException.context() : context,
                        "SMIME_ENCRYPT_FAILED",
                        "errorType=" + mailProcessingException.errorType().name()
                                + MailProcessingAuditEvents.detailPresence(mailProcessingException.getMessage()),
                        false);
                recordFailure(
                        mailProcessingException.context() != null ? mailProcessingException.context() : context,
                        mailProcessingException.getMessage());
                throw mailProcessingException;
            }
            log.error("S/MIME encryption failed: {}", e.getMessage(), e);
            recordAudit(context, "SMIME_ENCRYPT_FAILED",
                    "errorType=" + MailProcessingErrorType.ENCRYPTION
                            + MailProcessingAuditEvents.detailPresence(e.getMessage()), false);
            recordFailure(context, e.getMessage());
            throw new MailProcessingException(
                    MailProcessingErrorType.ENCRYPTION,
                    "S/MIME encryption failed: " + e.getMessage(),
                    context,
                    e);
        }
    }

    private void recordAudit(MailProcessingContext context, String action, String detail, boolean success) {
        MailProcessingAuditEvents.publish(
                domainEventPublisher,
                AuditLogType.EMAIL_ENCRYPTED,
                context,
                action,
                detail,
                success);
    }

    public String getStepName() {
        return "encrypt";
    }

    private MailProcessingContext context(Message<?> message) {
        return MailProcessingMessages.context(message);
    }

    private EncryptionPlan buildEncryptionPlan(MailProcessingContext context) {
        Map<EmailAddress, String> selectedCertificates = context.certificateSelection().recipientCertificates();
        if (selectedCertificates.isEmpty()) {
            return EncryptionPlan.failure("未找到收件人加密证书");
        }
        if (!selectedCertificates.keySet().containsAll(context.envelope().getRecipients())) {
            List<String> missingRecipients = context.envelope().getRecipients().stream()
                    .filter(recipient -> !selectedCertificates.containsKey(recipient))
                    .map(EmailAddress::getValue)
                    .toList();
            return EncryptionPlan.failure("以下收件人没有加密证书: " + String.join(", ", missingRecipients));
        }
        CryptoProfile profile = context.cryptoProfile();
        if (profile == null || !profile.isConcrete()) {
            return EncryptionPlan.failure("无法确定邮件加密Profile");
        }
        return EncryptionPlan.success(
                profile,
                new LinkedHashMap<>(selectedCertificates),
                new LinkedHashMap<>(context.certificateSelection().recipientCertificateThumbprints()));
    }

    private List<MailEncrypted> encryptedEvents(MailEnvelope envelope,
                                                Map<EmailAddress, String> recipientCerts,
                                                Map<EmailAddress, String> recipientThumbprints) {
        List<MailEncrypted> events = new ArrayList<>();
        if (recipientThumbprints == null || recipientThumbprints.isEmpty()) {
            return events;
        }
        recipientCerts.keySet().forEach(recipient -> {
            String thumbprint = recipientThumbprints.get(recipient);
            if (thumbprint != null && !thumbprint.isBlank()) {
                events.add(new MailEncrypted(
                        envelope.getMessageId(),
                        recipient,
                        new CertificateId(thumbprint)));
            }
        });
        return events;
    }

    private record EncryptionPlan(boolean success,
                                  CryptoProfile profile,
                                  Map<EmailAddress, String> certificates,
                                  Map<EmailAddress, String> thumbprints,
                                  String failureDetail) {
        static EncryptionPlan success(CryptoProfile profile,
                                      Map<EmailAddress, String> certificates,
                                      Map<EmailAddress, String> thumbprints) {
            return new EncryptionPlan(true, profile, certificates, thumbprints, null);
        }

        static EncryptionPlan failure(String detail) {
            return new EncryptionPlan(false, null, Map.of(), Map.of(), detail);
        }
    }

    private void recordSuccess(MailProcessingContext context, EncryptionPlan plan) {
        if (statusService == null) {
            return;
        }
        statusService.recordSmimeSuccess(
                context,
                MailProcessingStatusService.SmimeOperation.ENCRYPT,
                plan.profile() != null ? plan.profile().name() : null,
                null,
                plan.thumbprints().values().stream()
                        .filter(value -> value != null && !value.isBlank())
                        .toList(),
                plan.certificates().keySet().stream()
                        .map(EmailAddress::getValue)
                        .toList());
    }

    private void recordSkipped(MailProcessingContext context, String reason) {
        if (statusService != null) {
            statusService.recordSmimeSkipped(context, MailProcessingStatusService.SmimeOperation.ENCRYPT, reason);
        }
    }

    private void recordFailure(MailProcessingContext context, String detail) {
        if (statusService != null) {
            statusService.recordSmimeFailure(
                    context,
                    MailProcessingStatusService.SmimeOperation.ENCRYPT,
                    MailProcessingErrorType.ENCRYPTION,
                    detail);
        }
    }
}
