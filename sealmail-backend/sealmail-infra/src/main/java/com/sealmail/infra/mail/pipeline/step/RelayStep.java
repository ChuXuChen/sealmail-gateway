package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.policy.PreferredAlgorithm;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.config.properties.RelayProperties;
import com.sealmail.infra.mail.pipeline.MailRecordDisposition;
import com.sealmail.infra.mail.relay.SmtpRelayClient;
import com.sealmail.infra.mail.relay.SmtpRelayConnectionSettings;
import com.sealmail.infra.mail.relay.SmtpRelayRequest;
import com.sealmail.infra.mail.pipeline.MailPipelineStep;
import com.sealmail.infra.mail.pipeline.PipelineResult;
import com.sealmail.infra.crypto.util.PemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pipeline step: Relay processed mail to downstream SMTP server.
 */
@Component
public class RelayStep implements MailPipelineStep {

    private static final Logger log = LoggerFactory.getLogger(RelayStep.class);

    private final RelayProperties relayProperties;
    private final SmtpRelayClient smtpRelayClient;
    private final CertificateRepository certificateRepository;

    public RelayStep(RelayProperties relayProperties,
                     SmtpRelayClient smtpRelayClient,
                     CertificateRepository certificateRepository) {
        this.relayProperties = relayProperties;
        this.smtpRelayClient = smtpRelayClient;
        this.certificateRepository = certificateRepository;
    }

    @Override
    public PipelineResult execute(Message<byte[]> message) {
        MailEnvelope envelope = (MailEnvelope) message.getHeaders().get("mailEnvelope");
        if (envelope == null) {
            return PipelineResult.failure("Mail envelope not found in message headers");
        }

        byte[] mailContent = message.getPayload();
        if (mailContent == null || mailContent.length == 0) {
            return PipelineResult.failure("Mail content is empty");
        }

        PipelineResult relayGuard = validateEncryptedOutboundRelay(message, envelope, mailContent);
        if (relayGuard != null) {
            return relayGuard;
        }

        // Read from message headers or fall back to properties
        String host = readStringHeader(message, "relayHost", relayProperties.getHost());
        int port = readIntHeader(message, "relayPort", relayProperties.getPort());
        String username = readStringHeader(message, "relayUsername", relayProperties.getUsername());
        String password = readStringHeader(message, "relayPassword", relayProperties.getPassword());
        boolean useTls = readBooleanHeader(message, "relayUseTls", relayProperties.isUseTls());
        int timeout = readIntHeader(message, "relayTimeout", relayProperties.getTimeout());
        try {
            String envelopeFrom = readStringHeader(message, "relayEnvelopeFrom", null);
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

            return PipelineResult.success(mailContent);

        } catch (Exception e) {
            log.error("Relay step failed: {} - host: {}, port: {}, user: {}",
                    e.getMessage(), host, port, username, e);
            return PipelineResult.failure("Mail relay failed: " + e.getMessage());
        }
    }

    @Override
    public String getStepName() {
        return "relay";
    }

    private PipelineResult validateEncryptedOutboundRelay(Message<byte[]> message,
                                                          MailEnvelope envelope,
                                                          byte[] mailContent) {
        if (!isOutbound(message)) {
            return null;
        }
        boolean encryptionRequired = readBooleanHeader(message, "encryptionEnabled", false)
                || readBooleanHeader(message, "mustEncrypt", false)
                || readBooleanHeader(message, "smimeEncrypted", false)
                || isSmimeEncryptedPayload(mailContent);
        if (!encryptionRequired) {
            return null;
        }

        PreferredAlgorithm preference = preferredAlgorithm(message);
        EncryptionPlan plan = buildEncryptionPlan(envelope, preference);
        if (plan.success()) {
            return null;
        }

        String detail = "Refusing to relay encrypted outbound mail: " + plan.failureDetail();
        log.error(detail);
        return PipelineResult.quarantine(
                mailContent,
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

    private boolean isOutbound(Message<byte[]> message) {
        Object value = message.getHeaders().get("mailDirection");
        if (value instanceof MailDirection direction) {
            return direction == MailDirection.OUTBOUND;
        }
        return value instanceof String stringValue
                && MailDirection.OUTBOUND.name().equalsIgnoreCase(stringValue);
    }

    private PreferredAlgorithm preferredAlgorithm(Message<byte[]> message) {
        Object value = message.getHeaders().get("preferredAlgorithm");
        if (value instanceof PreferredAlgorithm algorithm) {
            return algorithm;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return PreferredAlgorithm.valueOf(stringValue);
        }
        return PreferredAlgorithm.AUTO;
    }

    private EncryptionPlan buildEncryptionPlan(MailEnvelope envelope, PreferredAlgorithm preference) {
        List<RecipientCertificateOptions> recipientOptions = new ArrayList<>();
        for (EmailAddress recipient : envelope.getRecipients()) {
            List<Certificate> certificates = certificateRepository.findTrustedForEncryption(recipient);
            recipientOptions.add(new RecipientCertificateOptions(
                    recipient,
                    selectGmCertificate(certificates),
                    selectStandardCertificate(certificates)));
        }

        if (preference == PreferredAlgorithm.GM_ONLY) {
            return planForSuite(recipientOptions, true, "以下收件人没有SM2加密证书: ");
        }
        if (preference == PreferredAlgorithm.STANDARD_ONLY) {
            return planForSuite(recipientOptions, false, "以下收件人没有RSA加密证书: ");
        }

        List<String> recipientsWithoutCertificates = recipientOptions.stream()
                .filter(option -> !option.supportsGm() && !option.supportsStandard())
                .map(option -> option.recipient().getValue())
                .toList();
        if (!recipientsWithoutCertificates.isEmpty()) {
            if (recipientsWithoutCertificates.size() == envelope.getRecipients().size()) {
                return EncryptionPlan.failure("未找到收件人加密证书");
            }
            return EncryptionPlan.failure("以下收件人没有加密证书: "
                    + String.join(", ", recipientsWithoutCertificates));
        }

        if (recipientOptions.stream().allMatch(RecipientCertificateOptions::supportsGm)
                || recipientOptions.stream().allMatch(RecipientCertificateOptions::supportsStandard)) {
            return EncryptionPlan.ok();
        }
        return EncryptionPlan.failure("多收件人无法共享同一加密策略，需所有收件人同时具备SM2或RSA加密证书: "
                + capabilitySummary(recipientOptions));
    }

    private EncryptionPlan planForSuite(List<RecipientCertificateOptions> recipientOptions,
                                        boolean gm,
                                        String missingPrefix) {
        List<String> missingRecipients = recipientOptions.stream()
                .filter(option -> gm ? !option.supportsGm() : !option.supportsStandard())
                .map(option -> option.recipient().getValue())
                .toList();
        if (!missingRecipients.isEmpty()) {
            return EncryptionPlan.failure(missingPrefix + String.join(", ", missingRecipients));
        }
        return EncryptionPlan.ok();
    }

    private Certificate selectGmCertificate(List<Certificate> certificates) {
        if (certificates == null) {
            return null;
        }
        return certificates.stream()
                .filter(this::isGmCertificate)
                .findFirst()
                .orElse(null);
    }

    private Certificate selectStandardCertificate(List<Certificate> certificates) {
        if (certificates == null) {
            return null;
        }
        return certificates.stream()
                .filter(this::isRsaCertificate)
                .findFirst()
                .orElse(null);
    }

    private boolean isRsaCertificate(Certificate certificate) {
        return "RSA".equals(certificateAlgorithm(certificate));
    }

    private boolean isGmCertificate(Certificate certificate) {
        String algorithm = certificateAlgorithm(certificate);
        return "SM2".equals(algorithm) || "EC".equals(algorithm) || "ECDSA".equals(algorithm);
    }

    private String certificateAlgorithm(Certificate certificate) {
        if (certificate.getAlgorithm() != null && !certificate.getAlgorithm().isBlank()) {
            return certificate.getAlgorithm().trim().toUpperCase(Locale.ROOT);
        }
        try {
            return PemUtils.parseCertificate(certificate.getPemContent())
                    .getPublicKey()
                    .getAlgorithm()
                    .trim()
                    .toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private String capabilitySummary(List<RecipientCertificateOptions> recipientOptions) {
        return recipientOptions.stream()
                .map(option -> option.recipient().getValue() + "=" + option.capabilityLabel())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private static String readStringHeader(Message<byte[]> message, String headerName, String fallback) {
        Object value = message.getHeaders().get(headerName);
        return value instanceof String stringValue ? stringValue : fallback;
    }

    private static int readIntHeader(Message<byte[]> message, String headerName, int fallback) {
        Object value = message.getHeaders().get(headerName);
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Integer.parseInt(stringValue);
        }
        return fallback;
    }

    private static boolean readBooleanHeader(Message<byte[]> message, String headerName, boolean fallback) {
        Object value = message.getHeaders().get(headerName);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Boolean.parseBoolean(stringValue);
        }
        return fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record RecipientCertificateOptions(EmailAddress recipient,
                                               Certificate gmCertificate,
                                               Certificate standardCertificate) {
        boolean supportsGm() {
            return gmCertificate != null;
        }

        boolean supportsStandard() {
            return standardCertificate != null;
        }

        String capabilityLabel() {
            if (supportsGm() && supportsStandard()) {
                return "GM,STANDARD";
            }
            if (supportsGm()) {
                return "GM";
            }
            if (supportsStandard()) {
                return "STANDARD";
            }
            return "NONE";
        }
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
