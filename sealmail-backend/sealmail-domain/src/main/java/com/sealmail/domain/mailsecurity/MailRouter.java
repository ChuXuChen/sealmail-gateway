package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateSelector;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.EncryptionPolicy;
import com.sealmail.domain.policy.PolicyPattern;
import com.sealmail.domain.policy.PreferredAlgorithm;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.shared.model.EmailAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 纯函数路由决策器 - 无状态、无副作用、确定性
 */
public class MailRouter {

    private final CertificateSelector certificateSelector;

    public MailRouter(CertificateSelector certificateSelector) {
        this.certificateSelector = certificateSelector;
    }

    public RoutingDecision route(MailEnvelope envelope, MailDirection direction,
                                 DomainConfig domainConfig, List<Certificate> availableCertificates,
                                 List<PolicyPattern> dlpPatterns, String mailContent) {

        if (direction == MailDirection.OUTBOUND) {
            return routeOutbound(envelope, domainConfig, availableCertificates, dlpPatterns, mailContent);
        } else {
            return routeInbound(envelope);
        }
    }

    private RoutingDecision routeOutbound(MailEnvelope envelope, DomainConfig domainConfig,
                                          List<Certificate> availableCertificates,
                                          List<PolicyPattern> dlpPatterns, String mailContent) {

        if (mailContent != null && !mailContent.isBlank()) {
            Optional<PolicyPattern> violatedPolicy = dlpPatterns.stream()
                    .filter(p -> p.matches(mailContent))
                    .findFirst();

            if (violatedPolicy.isPresent()) {
                PolicyPattern pattern = violatedPolicy.get();
                return switch (pattern.getViolationAction()) {
                    case QUARANTINE -> new RoutingDecision.Quarantine(
                            QuarantineReason.POLICY_VIOLATION,
                            "Violated DLP policy: " + pattern.getName()
                    );
                    case BLOCK -> new RoutingDecision.Quarantine(
                            QuarantineReason.POLICY_VIOLATION,
                            "Blocked by DLP policy: " + pattern.getName()
                    );
                    case MUST_ENCRYPT -> {
                        yield createRequiredEncryptionDecision(envelope, availableCertificates, domainConfig);
                    }
                    default -> createOutboundDecision(envelope, domainConfig, availableCertificates);
                };
            }
        }

        return createOutboundDecision(envelope, domainConfig, availableCertificates);
    }

    private RoutingDecision createOutboundDecision(MailEnvelope envelope, DomainConfig domainConfig,
                                                    List<Certificate> availableCertificates) {
        EncryptionPolicy policy = domainConfig.getEncryptionPolicy();

        if (policy == EncryptionPolicy.NO_ENCRYPTION) {
            if (domainConfig.isSigningEnabled()) {
                return new RoutingDecision.OutboundSign(envelope.getRecipients());
            }
            return new RoutingDecision.PassThrough();
        }

        RoutingDecision encryptDecision = createOptionalEncryptionDecision(envelope, availableCertificates, domainConfig);

        if (encryptDecision instanceof RoutingDecision.OutboundEncrypt) {
            return encryptDecision;
        }

        if (policy == EncryptionPolicy.MANDATORY) {
            return createRequiredEncryptionDecision(envelope, availableCertificates, domainConfig);
        }

        if (domainConfig.isSigningEnabled()) {
            return new RoutingDecision.OutboundSign(envelope.getRecipients());
        }

        return new RoutingDecision.PassThrough();
    }

    private RoutingDecision createOptionalEncryptionDecision(MailEnvelope envelope,
                                                            List<Certificate> availableCertificates,
                                                            DomainConfig domainConfig) {
        EncryptionPlan plan = buildEncryptionPlan(envelope, availableCertificates, domainConfig);
        if (!plan.success()) {
            return new RoutingDecision.PassThrough();
        }
        return new RoutingDecision.OutboundEncrypt(envelope.getRecipients());
    }

    private RoutingDecision createRequiredEncryptionDecision(MailEnvelope envelope,
                                                            List<Certificate> availableCertificates,
                                                            DomainConfig domainConfig) {
        EncryptionPlan plan = buildEncryptionPlan(envelope, availableCertificates, domainConfig);
        if (plan.success()) {
            return new RoutingDecision.OutboundEncrypt(envelope.getRecipients());
        }
        return new RoutingDecision.Quarantine(
                QuarantineReason.CERTIFICATE_MISSING,
                plan.failureDetail()
        );
    }

    private EncryptionPlan buildEncryptionPlan(MailEnvelope envelope,
                                               List<Certificate> availableCertificates,
                                               DomainConfig domainConfig) {
        List<RecipientCertificateOptions> recipientOptions = new ArrayList<>();

        for (EmailAddress recipient : envelope.getRecipients()) {
            List<Certificate> recipientCertificates = certificatesForRecipient(recipient, availableCertificates);
            recipientOptions.add(new RecipientCertificateOptions(
                    recipient,
                    supportsGm(recipientCertificates),
                    supportsStandard(recipientCertificates)));
        }

        if (recipientOptions.isEmpty()) {
            return EncryptionPlan.failure("No recipients available for encryption");
        }

        PreferredAlgorithm preference = domainConfig != null
                ? domainConfig.getPreferredAlgorithm()
                : PreferredAlgorithm.AUTO;

        if (preference == PreferredAlgorithm.GM_ONLY) {
            return allSupport(recipientOptions, true)
                    ? EncryptionPlan.ok()
                    : EncryptionPlan.failure("以下收件人没有SM2加密证书: "
                    + missingRecipients(recipientOptions, true));
        }
        if (preference == PreferredAlgorithm.STANDARD_ONLY) {
            return allSupport(recipientOptions, false)
                    ? EncryptionPlan.ok()
                    : EncryptionPlan.failure("以下收件人没有RSA加密证书: "
                    + missingRecipients(recipientOptions, false));
        }

        List<String> recipientsWithoutCertificates = recipientOptions.stream()
                .filter(option -> !option.supportsGm() && !option.supportsStandard())
                .map(option -> option.recipient().getValue())
                .toList();
        if (!recipientsWithoutCertificates.isEmpty()) {
            if (recipientsWithoutCertificates.size() == recipientOptions.size()) {
                return EncryptionPlan.failure("未找到收件人加密证书");
            }
            return EncryptionPlan.failure("以下收件人没有加密证书: "
                    + String.join(", ", recipientsWithoutCertificates));
        }

        if (allSupport(recipientOptions, true) || allSupport(recipientOptions, false)) {
            return EncryptionPlan.ok();
        }

        return EncryptionPlan.failure("多收件人无法共享同一加密策略，需所有收件人同时具备SM2或RSA加密证书: "
                + capabilitySummary(recipientOptions));
    }

    private List<Certificate> certificatesForRecipient(EmailAddress recipient, List<Certificate> availableCertificates) {
        if (availableCertificates == null) {
            return List.of();
        }
        return availableCertificates.stream()
                .filter(Certificate::isSuitableForEncryption)
                .filter(cert -> cert.getOwner().equals(recipient))
                .toList();
    }

    private boolean supportsGm(List<Certificate> certificates) {
        return certificates.stream().anyMatch(this::isGmCertificate);
    }

    private boolean supportsStandard(List<Certificate> certificates) {
        return certificates.stream().anyMatch(this::isStandardCertificate);
    }

    private boolean isGmCertificate(Certificate certificate) {
        String algorithm = normalizeAlgorithm(certificate.getAlgorithm());
        return "SM2".equals(algorithm) || "EC".equals(algorithm) || "ECDSA".equals(algorithm);
    }

    private boolean isStandardCertificate(Certificate certificate) {
        return "RSA".equals(normalizeAlgorithm(certificate.getAlgorithm()));
    }

    private String normalizeAlgorithm(String algorithm) {
        return algorithm == null ? "UNKNOWN" : algorithm.trim().toUpperCase(Locale.ROOT);
    }

    private boolean allSupport(List<RecipientCertificateOptions> recipientOptions, boolean gm) {
        return recipientOptions.stream().allMatch(option -> gm ? option.supportsGm() : option.supportsStandard());
    }

    private String missingRecipients(List<RecipientCertificateOptions> recipientOptions, boolean gm) {
        return recipientOptions.stream()
                .filter(option -> gm ? !option.supportsGm() : !option.supportsStandard())
                .map(option -> option.recipient().getValue())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private String capabilitySummary(List<RecipientCertificateOptions> recipientOptions) {
        return recipientOptions.stream()
                .map(option -> option.recipient().getValue() + "=" + option.capabilityLabel())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private RoutingDecision routeInbound(MailEnvelope envelope) {
        return new RoutingDecision.PassThrough();
    }

    private record RecipientCertificateOptions(EmailAddress recipient,
                                               boolean supportsGm,
                                               boolean supportsStandard) {
        String capabilityLabel() {
            if (supportsGm && supportsStandard) {
                return "GM,STANDARD";
            }
            if (supportsGm) {
                return "GM";
            }
            if (supportsStandard) {
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
