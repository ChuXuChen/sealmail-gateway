package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.mailsecurity.CryptoProfile;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.mailsecurity.RelayProfile;
import com.sealmail.infra.config.RelayPolicyService;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.mail.relay.SmtpRelayClient;
import com.sealmail.infra.mail.relay.SmtpRelayConnectionSettings;
import com.sealmail.infra.mail.relay.SmtpRelayRequest;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.mail.pipeline.MailProcessingAuditEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Pipeline step: Relay processed mail to downstream SMTP server.
 */
@Component
public class RelayStep {

    private static final Logger log = LoggerFactory.getLogger(RelayStep.class);

    private final RelayPolicyService relayPolicyService;
    private final SmtpRelayClient smtpRelayClient;
    private final DomainEventPublisher domainEventPublisher;

    public RelayStep(RelayPolicyService relayPolicyService,
                     SmtpRelayClient smtpRelayClient,
                     DomainEventPublisher domainEventPublisher) {
        this.relayPolicyService = relayPolicyService;
        this.smtpRelayClient = smtpRelayClient;
        this.domainEventPublisher = domainEventPublisher;
    }

    public Message<byte[]> execute(Message<byte[]> message) {
        MailProcessingContext context = context(message);
        MailEnvelope envelope = context != null ? context.envelope() : null;
        if (envelope == null) {
            throw new MailProcessingException(
                    MailProcessingErrorType.RELAY,
                    "Mail processing context not found in message headers",
                    context);
        }

        byte[] mailContent = message.getPayload();
        if (mailContent == null || mailContent.length == 0) {
            recordRelayAudit(context, "SMTP_RELAY_FAILED", "Mail content is empty", false);
            throw new MailProcessingException(
                    MailProcessingErrorType.RELAY,
                    "Mail content is empty",
                    context);
        }

        validateEncryptedOutboundRelay(context, mailContent);

        RelayProfile relayProfile = context.relayProfile();
        if (relayProfile == null) {
            relayProfile = relayPolicyService.activeRelayProfile();
        }
        if (relayProfile == null) {
            recordRelayAudit(context, "SMTP_RELAY_FAILED",
                    "Direct SMTP relay policy is disabled or not configured", false);
            throw new MailProcessingException(
                    MailProcessingErrorType.RELAY,
                    "Direct SMTP relay policy is disabled or not configured",
                    context,
                    MailRecordDisposition.EXCEPTION,
                    true,
                    null);
        }
        String host = relayProfile.host();
        int port = relayProfile.port();
        String username = relayProfile.username();
        String password = relayProfile.password();
        int timeout = relayProfile.timeout();
        try {
            String envelopeFrom = relayProfile.envelopeFrom();
            if (!hasText(envelopeFrom)) {
                envelopeFrom = hasText(username) ? username : envelope.getSender().getValue();
            }
            List<String> recipients = envelope.getRecipients().stream()
                    .map(addr -> addr.getValue())
                    .toList();
            SmtpRelayConnectionSettings connection = new SmtpRelayConnectionSettings(
                    host,
                    port,
                    username,
                    password,
                    timeout,
                    relayProfile.transportProfile()
            );

            log.info("=== RELAYING TO: {}:{} profile={} userConfigured={} ===",
                    host,
                    port,
                    relayProfile.transportProfile(),
                    hasText(username));
            smtpRelayClient.send(new SmtpRelayRequest(connection, envelopeFrom, recipients, mailContent));

            log.info("=== Mail successfully relayed ===");
            log.info("  Relay Host: {}:{}", host, port);
            log.info("  Envelope From: {}", envelopeFrom);
            log.info("  To: {}", envelope.getRecipients());
            log.info("  Size: {} bytes", mailContent.length);
            log.info("=================================");
            recordRelayAudit(context, "SMTP_RELAY", MailProcessingAuditEvents.relayProfileSummary(relayProfile), true);

            return message;

        } catch (Exception e) {
            if (e instanceof MailProcessingException mailProcessingException) {
                recordRelayAudit(
                        mailProcessingException.context() != null ? mailProcessingException.context() : context,
                        "SMTP_RELAY_FAILED",
                        "errorType=" + mailProcessingException.errorType().name()
                                + ", retryable=" + mailProcessingException.retryable()
                                + MailProcessingAuditEvents.detailPresence(mailProcessingException.getMessage()),
                        false);
                throw mailProcessingException;
            }
            log.error("Relay step failed: {} - host: {}, port: {}, userConfigured: {}",
                    e.getMessage(), host, port, hasText(username), e);
            recordRelayAudit(context, "SMTP_RELAY_FAILED",
                    "errorType=" + MailProcessingErrorType.RELAY
                            + MailProcessingAuditEvents.detailPresence(e.getMessage())
                            + ", " + MailProcessingAuditEvents.relayProfileSummary(relayProfile), false);
            throw new MailProcessingException(
                    MailProcessingErrorType.RELAY,
                    "Mail relay failed: " + e.getMessage(),
                    context,
                    MailRecordDisposition.EXCEPTION,
                    true,
                    e);
        }
    }

    public String getStepName() {
        return "relay";
    }

    private void validateEncryptedOutboundRelay(MailProcessingContext context, byte[] mailContent) {
        if (!isOutbound(context)) {
            return;
        }
        boolean encryptionRequired = context.decision().encryptionRequired()
                || context.decision().mustEncrypt()
                || context.smimeEncrypted()
                || isSmimeEncryptedPayload(mailContent);
        if (!encryptionRequired) {
            return;
        }

        CryptoProfile profile = context.cryptoProfile();
        if (profile == null || !profile.isConcrete()) {
            throwRelayGuardException(context, "Refusing to relay encrypted outbound mail: 无法确定邮件加密Profile");
        }

        if (context.certificateSelection().recipientCertificates().isEmpty()) {
            throwRelayGuardException(context, "Refusing to relay encrypted outbound mail: 未找到收件人加密证书");
        }

        if (!context.certificateSelection().recipientCertificates().keySet().containsAll(context.envelope().getRecipients())) {
            List<String> missingRecipients = context.envelope().getRecipients().stream()
                    .filter(recipient -> !context.certificateSelection().recipientCertificates().containsKey(recipient))
                    .map(com.sealmail.domain.shared.model.EmailAddress::getValue)
                    .toList();
            throwRelayGuardException(context, "Refusing to relay encrypted outbound mail: 以下收件人没有加密证书: "
                    + String.join(", ", missingRecipients));
        }
    }

    private void throwRelayGuardException(MailProcessingContext context, String detail) {
        log.error(detail);
        recordRelayAudit(context, "SMTP_RELAY_FAILED", detail, false);
        throw new MailProcessingException(
                MailProcessingErrorType.RELAY,
                detail,
                context,
                MailRecordDisposition.EXCEPTION,
                false,
                null);
    }

    private boolean isSmimeEncryptedPayload(byte[] mailContent) {
        if (mailContent == null || mailContent.length == 0) {
            return false;
        }
        String content = new String(mailContent, 0, Math.min(mailContent.length, 4096), StandardCharsets.ISO_8859_1)
                .toLowerCase(Locale.ROOT);
        return content.contains("application/pkcs7-mime")
                || content.contains("application/x-pkcs7-mime")
                || content.contains("smime-type=enveloped-data")
                || content.contains("name=\"smime.p7m\"")
                || content.contains("name=smime.p7m");
    }

    private boolean isOutbound(MailProcessingContext context) {
        return context.direction() == MailDirection.OUTBOUND;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private MailProcessingContext context(Message<?> message) {
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }

    private void recordRelayAudit(MailProcessingContext context, String action, String detail, boolean success) {
        MailProcessingAuditEvents.publish(
                domainEventPublisher,
                AuditLogType.EMAIL_RELAYED,
                context,
                action,
                detail,
                success);
    }

}
