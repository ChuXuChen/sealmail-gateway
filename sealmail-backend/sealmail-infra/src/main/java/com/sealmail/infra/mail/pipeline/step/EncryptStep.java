package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.certificate.spi.SMIMEEncryptionSuite;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.mailsecurity.event.MailEncrypted;
import com.sealmail.domain.policy.PreferredAlgorithm;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.crypto.util.PemUtils;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.mail.pipeline.MailPipelineStep;
import com.sealmail.infra.mail.pipeline.PipelineResult;
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
public class EncryptStep implements MailPipelineStep {

    private static final Logger log = LoggerFactory.getLogger(EncryptStep.class);

    private final SMIMEOperations smimeOperations;
    private final CertificateRepository certificateRepository;

    public EncryptStep(SMIMEOperations smimeOperations,
                       CertificateRepository certificateRepository) {
        this.smimeOperations = smimeOperations;
        this.certificateRepository = certificateRepository;
    }

    @Override
    public PipelineResult execute(Message<byte[]> message) {
        MailProcessingContext context = context(message);
        MailEnvelope envelope = context != null ? context.envelope() : null;
        if (envelope == null) {
            return PipelineResult.failure("Mail processing context not found in message headers");
        }

        boolean encryptionEnabled = context.decision().encryptionRequired();
        boolean mustEncrypt = context.decision().mustEncrypt();
        if (!encryptionEnabled && !mustEncrypt) {
            return PipelineResult.success(message.getPayload());
        }

        try {
            PreferredAlgorithm preference = context.preferredAlgorithm();

            EncryptionPlan plan = buildEncryptionPlan(envelope, preference);
            if (!plan.success()) {
                return PipelineResult.quarantine(
                        message.getPayload(),
                        "CERTIFICATE_MISSING",
                        mustEncrypt ? "DLP MUST_ENCRYPT: " + plan.failureDetail() : plan.failureDetail(),
                        MailRecordDisposition.EXCEPTION);
            }

            byte[] payload = message.getPayload();
            int originalSize = payload.length;
            List<String> certChain = plan.certificates().values().stream().toList();
            payload = smimeOperations.encryptMultiple(payload, certChain, plan.suite());
            List<MailEncrypted> events = encryptedEvents(envelope, plan.certificates(), plan.thumbprints());
            for (EmailAddress recipient : plan.certificates().keySet()) {
                log.info("  Encrypted for recipient: {} using {}", recipient, plan.suite());
            }

            log.info("=== S/MIME ENCRYPTION COMPLETED ===");
            log.info("  Original size: {} bytes", originalSize);
            log.info("  Encrypted size: {} bytes", payload.length);
            log.info("  First 100 chars: {}", new String(payload).replaceAll("[\r\n]", " ").substring(0, Math.min(100, payload.length)));

            context = context.withSmimeEncryption(plan.suite().name(), List.copyOf(plan.certificates().keySet()));
            return PipelineResult.successWithHeaders(payload, Map.of(MailProcessingHeaders.CONTEXT, context), events);

        } catch (Exception e) {
            log.error("S/MIME encryption failed: {}", e.getMessage(), e);
            return PipelineResult.quarantine(
                    message.getPayload(),
                    "ENCRYPTION_FAILED",
                    "S/MIME encryption failed: " + e.getMessage(),
                    MailRecordDisposition.EXCEPTION);
        }
    }

    @Override
    public String getStepName() {
        return "encrypt";
    }

    private MailProcessingContext context(Message<?> message) {
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
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
            return planForSuite(recipientOptions, SMIMEEncryptionSuite.GM,
                    "以下收件人没有SM2加密证书: ");
        }
        if (preference == PreferredAlgorithm.STANDARD_ONLY) {
            return planForSuite(recipientOptions, SMIMEEncryptionSuite.STANDARD,
                    "以下收件人没有RSA加密证书: ");
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

        if (recipientOptions.stream().allMatch(RecipientCertificateOptions::supportsGm)) {
            return planForSuite(recipientOptions, SMIMEEncryptionSuite.GM,
                    "以下收件人没有SM2加密证书: ");
        }
        if (recipientOptions.stream().allMatch(RecipientCertificateOptions::supportsStandard)) {
            return planForSuite(recipientOptions, SMIMEEncryptionSuite.STANDARD,
                    "以下收件人没有RSA加密证书: ");
        }
        return EncryptionPlan.failure("多收件人无法共享同一加密策略，需所有收件人同时具备SM2或RSA加密证书: "
                + capabilitySummary(recipientOptions));
    }

    private EncryptionPlan planForSuite(List<RecipientCertificateOptions> recipientOptions,
                                        SMIMEEncryptionSuite suite,
                                        String missingPrefix) {
        List<String> missingRecipients = recipientOptions.stream()
                .filter(option -> option.certificateFor(suite) == null)
                .map(option -> option.recipient().getValue())
                .toList();
        if (!missingRecipients.isEmpty()) {
            return EncryptionPlan.failure(missingPrefix + String.join(", ", missingRecipients));
        }

        Map<EmailAddress, String> certs = new LinkedHashMap<>();
        Map<EmailAddress, String> thumbprints = new LinkedHashMap<>();
        for (RecipientCertificateOptions option : recipientOptions) {
            Certificate certificate = option.certificateFor(suite);
            certs.put(option.recipient(), certificate.getPemContent());
            thumbprints.put(option.recipient(), certificate.getId().getThumbprint());
        }
        return EncryptionPlan.success(suite, certs, thumbprints);
    }

    private Certificate selectGmCertificate(List<Certificate> certificates) {
        if (certificates == null) {
            return null;
        }
        return certificates.stream()
                .filter(this::isSm2Certificate)
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
        String algorithm = certificateAlgorithm(certificate);
        return "RSA".equals(algorithm);
    }

    private boolean isSm2Certificate(Certificate certificate) {
        String curveOid = certificateCurveOid(certificate);
        if (curveOid != null) {
            return "1.2.156.10197.1.301".equals(curveOid);
        }
        return "SM2".equals(certificateAlgorithm(certificate));
    }

    private String certificateAlgorithm(Certificate certificate) {
        if (certificate.getAlgorithm() != null && !certificate.getAlgorithm().isBlank()) {
            return certificate.getAlgorithm();
        }
        try {
            return PemUtils.parseCertificate(certificate.getPemContent()).getPublicKey().getAlgorithm();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private String certificateCurveOid(Certificate certificate) {
        try {
            java.security.cert.X509Certificate x509 = PemUtils.parseCertificate(certificate.getPemContent());
            org.bouncycastle.cert.X509CertificateHolder holder =
                    new org.bouncycastle.cert.X509CertificateHolder(x509.getEncoded());
            Object parameters = holder.getSubjectPublicKeyInfo().getAlgorithm().getParameters();
            return parameters != null ? parameters.toString() : null;
        } catch (Exception e) {
            return null;
        }
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

    private String capabilitySummary(List<RecipientCertificateOptions> recipientOptions) {
        return recipientOptions.stream()
                .map(option -> option.recipient().getValue() + "=" + option.capabilityLabel())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
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

        Certificate certificateFor(SMIMEEncryptionSuite suite) {
            return suite == SMIMEEncryptionSuite.GM ? gmCertificate : standardCertificate;
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

    private record EncryptionPlan(boolean success,
                                  SMIMEEncryptionSuite suite,
                                  Map<EmailAddress, String> certificates,
                                  Map<EmailAddress, String> thumbprints,
                                  String failureDetail) {
        static EncryptionPlan success(SMIMEEncryptionSuite suite,
                                      Map<EmailAddress, String> certificates,
                                      Map<EmailAddress, String> thumbprints) {
            return new EncryptionPlan(true, suite, certificates, thumbprints, null);
        }

        static EncryptionPlan failure(String detail) {
            return new EncryptionPlan(false, null, Map.of(), Map.of(), detail);
        }
    }
}
