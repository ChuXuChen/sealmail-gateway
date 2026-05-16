package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.mailsecurity.CryptoProfileSelector;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.policy.PreferredAlgorithm;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.config.properties.RelayProperties;
import com.sealmail.domain.mailsecurity.RelayProfile;
import com.sealmail.infra.mail.relay.SmtpRelayClient;
import com.sealmail.infra.mail.relay.SmtpRelayConnectionSettings;
import com.sealmail.infra.mail.relay.SmtpRelayRequest;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.mail.pipeline.MailProcessingMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Pipeline step: Relay processed mail to downstream SMTP server.
 */
@Component
public class RelayStep {

    private static final Logger log = LoggerFactory.getLogger(RelayStep.class);

    private final RelayProperties relayProperties;
    private final SmtpRelayClient smtpRelayClient;
    private final CertificateRepository certificateRepository;
    private final CryptoProfileSelector cryptoProfileSelector;

    public RelayStep(RelayProperties relayProperties,
                     SmtpRelayClient smtpRelayClient,
                     CertificateRepository certificateRepository) {
        this(relayProperties, smtpRelayClient, certificateRepository, new CryptoProfileSelector());
    }

    @Autowired
    public RelayStep(RelayProperties relayProperties,
                     SmtpRelayClient smtpRelayClient,
                     CertificateRepository certificateRepository,
                     CryptoProfileSelector cryptoProfileSelector) {
        this.relayProperties = relayProperties;
        this.smtpRelayClient = smtpRelayClient;
        this.certificateRepository = certificateRepository;
        this.cryptoProfileSelector = cryptoProfileSelector;
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
            throw new MailProcessingException(
                    MailProcessingErrorType.RELAY,
                    "Mail content is empty",
                    context);
        }

        Message<byte[]> relayGuard = validateEncryptedOutboundRelay(message, context, envelope, mailContent);
        if (relayGuard != null) {
            return relayGuard;
        }

        RelayProfile relayProfile = context.relayProfile();
        String host = relayProfile != null ? relayProfile.host() : relayProperties.getHost();
        int port = relayProfile != null ? relayProfile.port() : relayProperties.getPort();
        String username = relayProfile != null ? relayProfile.username() : relayProperties.getUsername();
        String password = relayProfile != null ? relayProfile.password() : relayProperties.getPassword();
        boolean useTls = relayProfile != null ? relayProfile.useTls() : relayProperties.isUseTls();
        int timeout = relayProfile != null ? relayProfile.timeout() : relayProperties.getTimeout();
        try {
            String envelopeFrom = relayProfile != null
                    ? relayProfile.envelopeFrom()
                    : null;
            if (!hasText(envelopeFrom)) {
                envelopeFrom = hasText(username) ? username : envelope.getSender().getValue();
            }
            List<String> recipients = envelope.getRecipients().stream()
                    .map(addr -> addr.getValue())
                    .toList();
            SmtpRelayConnectionSettings connection = new SmtpRelayConnectionSettings(
                    host,
                    port,
                    useTls,
                    username,
                    password,
                    timeout
            );

            log.info("=== RELAYING TO: {}:{} mode={} user: {} ===",
                    host, port, connection.useImplicitTls() ? "SMTPS" : (connection.useStartTls() ? "STARTTLS" : "PLAIN"), username);
            smtpRelayClient.send(new SmtpRelayRequest(connection, envelopeFrom, recipients, mailContent));

            log.info("=== Mail successfully relayed ===");
            log.info("  Relay Host: {}:{}", host, port);
            log.info("  Envelope From: {}", envelopeFrom);
            log.info("  To: {}", envelope.getRecipients());
            log.info("  Size: {} bytes", mailContent.length);
            log.info("=================================");

            return message;

        } catch (Exception e) {
            log.error("Relay step failed: {} - host: {}, port: {}, user: {}",
                    e.getMessage(), host, port, username, e);
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

    private Message<byte[]> validateEncryptedOutboundRelay(Message<byte[]> message,
                                                           MailProcessingContext context,
                                                           MailEnvelope envelope,
                                                           byte[] mailContent) {
        if (!isOutbound(context)) {
            return null;
        }
        boolean encryptionRequired = context.decision().encryptionRequired()
                || context.decision().mustEncrypt()
                || context.smimeEncrypted()
                || isSmimeEncryptedPayload(mailContent);
        if (!encryptionRequired) {
            return null;
        }

        PreferredAlgorithm preference = context.preferredAlgorithm();
        EncryptionPlan plan = buildEncryptionPlan(envelope, preference);
        if (plan.success()) {
            return null;
        }

        String detail = "Refusing to relay encrypted outbound mail: " + plan.failureDetail();
        log.error(detail);
        return MailProcessingMessages.quarantine(
                message,
                "CERTIFICATE_MISSING",
                detail,
                MailRecordDisposition.EXCEPTION);
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

    private EncryptionPlan buildEncryptionPlan(MailEnvelope envelope, PreferredAlgorithm preference) {
        Map<EmailAddress, List<Certificate>> certificatesByRecipient = new LinkedHashMap<>();
        for (EmailAddress recipient : envelope.getRecipients()) {
            certificatesByRecipient.put(recipient, certificateRepository.findTrustedForEncryption(recipient));
        }
        CryptoProfileSelector.EncryptionProfilePlan plan = cryptoProfileSelector.encryptionPlan(
                certificatesByRecipient,
                preference);
        return plan.success() ? EncryptionPlan.ok() : EncryptionPlan.failure(plan.failureDetail());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private MailProcessingContext context(Message<?> message) {
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }

    private record EncryptionPlan(boolean success, String failureDetail) {
        static EncryptionPlan ok() {
            return new EncryptionPlan(true, null);
        }

        static EncryptionPlan failure(String detail) {
            return new EncryptionPlan(false, detail);
        }
    }
}
