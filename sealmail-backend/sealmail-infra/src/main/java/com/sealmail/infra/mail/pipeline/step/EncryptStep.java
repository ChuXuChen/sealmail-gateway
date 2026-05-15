package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.event.MailEncrypted;
import com.sealmail.domain.policy.PreferredAlgorithm;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.config.properties.DlpProperties;
import com.sealmail.infra.crypto.util.PemUtils;
import com.sealmail.infra.mail.pipeline.MailPipelineStep;
import com.sealmail.infra.mail.pipeline.MailRecordDisposition;
import com.sealmail.infra.mail.pipeline.PipelineResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Outbound pipeline step: Encrypt outgoing mail with S/MIME for each recipient.
 */
@Component
public class EncryptStep implements MailPipelineStep {

    private static final Logger log = LoggerFactory.getLogger(EncryptStep.class);

    private final SMIMEOperations smimeOperations;
    private final CertificateRepository certificateRepository;
    private final DlpProperties dlpProperties;

    public EncryptStep(SMIMEOperations smimeOperations,
                       CertificateRepository certificateRepository,
                       DlpProperties dlpProperties) {
        this.smimeOperations = smimeOperations;
        this.certificateRepository = certificateRepository;
        this.dlpProperties = dlpProperties;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PipelineResult execute(Message<byte[]> message) {
        MailEnvelope envelope = (MailEnvelope) message.getHeaders().get("mailEnvelope");
        if (envelope == null) {
            return PipelineResult.failure("Mail envelope not found in message headers");
        }

        Boolean encryptionEnabled = (Boolean) message.getHeaders().get("encryptionEnabled");
        boolean mustEncrypt = Boolean.TRUE.equals(message.getHeaders().get("mustEncrypt"))
                || "true".equals(message.getHeaders().get("mustEncrypt"));
        if ((encryptionEnabled == null || !encryptionEnabled) && !mustEncrypt) {
            return PipelineResult.success(message.getPayload());
        }

        try {
            Map<EmailAddress, String> recipientCerts =
                    (Map<EmailAddress, String>) message.getHeaders().get("recipientCertificates");
            Map<EmailAddress, String> recipientThumbprints =
                    (Map<EmailAddress, String>) message.getHeaders().get("recipientCertificateThumbprints");

            String prefStr = (String) message.getHeaders().get("preferredAlgorithm");
            PreferredAlgorithm preference = prefStr != null ? PreferredAlgorithm.valueOf(prefStr) : PreferredAlgorithm.AUTO;
            if ((recipientCerts == null || recipientCerts.isEmpty()) && mustEncrypt) {
                RecipientCertificateSelection selection = loadRecipientCertificates(envelope);
                recipientCerts = selection.certificates();
                recipientThumbprints = selection.thumbprints();
            }

            if (recipientCerts == null || recipientCerts.isEmpty()) {
                if (mustEncrypt) {
                    return PipelineResult.quarantine(
                            message.getPayload(),
                            "CERTIFICATE_MISSING",
                            "DLP MUST_ENCRYPT: 未找到收件人加密证书",
                            mustEncryptFailureDisposition());
                }
                if (preference == PreferredAlgorithm.GM_ONLY) {
                    return PipelineResult.quarantine(
                            message.getPayload(), "CERTIFICATE_MISSING", "GM_ONLY策略：未找到SM2加密证书");
                }
                if (preference == PreferredAlgorithm.STANDARD_ONLY) {
                    return PipelineResult.quarantine(
                            message.getPayload(), "CERTIFICATE_MISSING", "STANDARD_ONLY策略：未找到RSA加密证书");
                }
                return PipelineResult.success(message.getPayload());
            }

            // 根据算法偏好过滤证书
            Map<EmailAddress, String> filteredCerts = new java.util.HashMap<>();
            for (Map.Entry<EmailAddress, String> entry : recipientCerts.entrySet()) {
                try {
                    String alg = PemUtils.parseCertificate(entry.getValue()).getPublicKey().getAlgorithm();
                    log.info("收件人 {} 证书算法: {}", entry.getKey(), alg);
                    boolean isGm = "EC".equals(alg) || "ECDSA".equals(alg);
                    boolean isStandard = "RSA".equals(alg);

                    if (preference == PreferredAlgorithm.GM_ONLY && isGm) {
                        filteredCerts.put(entry.getKey(), entry.getValue());
                        log.info("选择SM2证书用于国密加密");
                    } else if (preference == PreferredAlgorithm.STANDARD_ONLY && isStandard) {
                        filteredCerts.put(entry.getKey(), entry.getValue());
                        log.info("选择RSA证书用于标准加密");
                    } else if (preference == PreferredAlgorithm.AUTO) {
                        // AUTO: 优先国密，其次国际
                        if (isGm) {
                            filteredCerts.put(entry.getKey(), entry.getValue());
                            log.info("选择SM2证书用于国密加密");
                        } else if (isStandard) {
                            filteredCerts.put(entry.getKey(), entry.getValue());
                            log.info("选择RSA证书用于AES加密");
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析证书失败: {}", e.getMessage());
                }
            }

            if (filteredCerts.isEmpty()) {
                if (preference == PreferredAlgorithm.GM_ONLY) {
                    if (mustEncrypt) {
                        return PipelineResult.quarantine(
                                message.getPayload(),
                                "CERTIFICATE_MISSING",
                                "DLP MUST_ENCRYPT: 收件人证书中没有SM2算法",
                                mustEncryptFailureDisposition());
                    }
                    return PipelineResult.quarantine(
                            message.getPayload(), "CERTIFICATE_MISSING", "GM_ONLY策略：收件人证书中没有SM2算法");
                }
                if (preference == PreferredAlgorithm.STANDARD_ONLY) {
                    if (mustEncrypt) {
                        return PipelineResult.quarantine(
                                message.getPayload(),
                                "CERTIFICATE_MISSING",
                                "DLP MUST_ENCRYPT: 收件人证书中没有RSA算法",
                                mustEncryptFailureDisposition());
                    }
                    return PipelineResult.quarantine(
                            message.getPayload(), "CERTIFICATE_MISSING", "STANDARD_ONLY策略：收件人证书中没有RSA算法");
                }
                filteredCerts = recipientCerts;
            }

            byte[] payload = message.getPayload();
            int originalSize = payload.length;
            List<String> certChain = filteredCerts.values().stream().toList();
            payload = smimeOperations.encryptMultiple(payload, certChain);
            List<MailEncrypted> events = encryptedEvents(envelope, filteredCerts, recipientThumbprints);
            for (EmailAddress recipient : filteredCerts.keySet()) {
                log.info("  Encrypted for recipient: {}", recipient);
            }

            log.info("=== S/MIME ENCRYPTION COMPLETED ===");
            log.info("  Original size: {} bytes", originalSize);
            log.info("  Encrypted size: {} bytes", payload.length);
            log.info("  First 100 chars: {}", new String(payload).replaceAll("[\r\n]", " ").substring(0, Math.min(100, payload.length)));

            return PipelineResult.success(payload, events);

        } catch (Exception e) {
            log.error("S/MIME encryption failed: {}", e.getMessage(), e);
            return PipelineResult.quarantine(
                    message.getPayload(),
                    "ENCRYPTION_FAILED",
                    "S/MIME encryption failed: " + e.getMessage(),
                    mustEncrypt ? mustEncryptFailureDisposition() : MailRecordDisposition.EXCEPTION);
        }
    }

    @Override
    public String getStepName() {
        return "encrypt";
    }

    private RecipientCertificateSelection loadRecipientCertificates(MailEnvelope envelope) {
        Map<EmailAddress, String> certificates = new java.util.HashMap<>();
        Map<EmailAddress, String> thumbprints = new java.util.HashMap<>();
        for (EmailAddress recipient : envelope.getRecipients()) {
            certificateRepository.findTrustedForEncryption(recipient).stream()
                    .findFirst()
                    .ifPresent(certificate -> {
                        certificates.put(recipient, certificate.getPemContent());
                        thumbprints.put(recipient, certificate.getId().getThumbprint());
                    });
        }
        return new RecipientCertificateSelection(certificates, thumbprints);
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

    private MailRecordDisposition mustEncryptFailureDisposition() {
        return dlpProperties.isQuarantineEncryptionFailures()
                ? MailRecordDisposition.DLP_QUARANTINE
                : MailRecordDisposition.EXCEPTION;
    }

    private record RecipientCertificateSelection(Map<EmailAddress, String> certificates,
                                                 Map<EmailAddress, String> thumbprints) {
    }
}
