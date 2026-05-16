package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.EncryptionPolicy;
import com.sealmail.domain.policy.PolicyPattern;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.shared.model.EmailAddress;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 纯函数路由决策器 - 无状态、无副作用、确定性
 */
public class MailRouter {

    private final CryptoProfileSelector cryptoProfileSelector;

    public MailRouter() {
        this.cryptoProfileSelector = new CryptoProfileSelector();
    }

    public RoutingDecision route(MailEnvelope envelope, MailDirection direction,
                                 DomainConfig domainConfig, List<Certificate> availableCertificates,
                                 List<PolicyPattern> dlpPatterns, String mailContent,
                                 CryptoProfile requestedProfile) {
        if (direction == MailDirection.OUTBOUND) {
            return routeOutbound(envelope, domainConfig, availableCertificates, dlpPatterns, mailContent, requestedProfile);
        } else {
            return routeInbound(envelope);
        }
    }

    private RoutingDecision routeOutbound(MailEnvelope envelope, DomainConfig domainConfig,
                                          List<Certificate> availableCertificates,
                                          List<PolicyPattern> dlpPatterns, String mailContent,
                                          CryptoProfile requestedProfile) {

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
                        yield createRequiredEncryptionDecision(envelope, availableCertificates, requestedProfile);
                    }
                    default -> createOutboundDecision(envelope, domainConfig, availableCertificates, requestedProfile);
                };
            }
        }

        return createOutboundDecision(envelope, domainConfig, availableCertificates, requestedProfile);
    }

    private RoutingDecision createOutboundDecision(MailEnvelope envelope, DomainConfig domainConfig,
                                                   List<Certificate> availableCertificates,
                                                   CryptoProfile requestedProfile) {
        EncryptionPolicy policy = domainConfig.getEncryptionPolicy();

        if (policy == EncryptionPolicy.NO_ENCRYPTION) {
            if (domainConfig.isSigningEnabled()) {
                return new RoutingDecision.OutboundSign(envelope.getRecipients());
            }
            return new RoutingDecision.PassThrough();
        }

        RoutingDecision encryptDecision = createOptionalEncryptionDecision(
                envelope,
                availableCertificates,
                domainConfig,
                requestedProfile);

        if (encryptDecision instanceof RoutingDecision.OutboundEncrypt) {
            return encryptDecision;
        }

        if (policy == EncryptionPolicy.MANDATORY) {
            return createRequiredEncryptionDecision(envelope, availableCertificates, requestedProfile);
        }

        if (domainConfig.isSigningEnabled()) {
            return new RoutingDecision.OutboundSign(envelope.getRecipients());
        }

        return new RoutingDecision.PassThrough();
    }

    private RoutingDecision createOptionalEncryptionDecision(MailEnvelope envelope,
                                                            List<Certificate> availableCertificates,
                                                            DomainConfig domainConfig,
                                                            CryptoProfile requestedProfile) {
        EncryptionPlan plan = buildEncryptionPlan(envelope, availableCertificates, requestedProfile);
        if (!plan.success()) {
            return new RoutingDecision.PassThrough();
        }
        return new RoutingDecision.OutboundEncrypt(envelope.getRecipients());
    }

    private RoutingDecision createRequiredEncryptionDecision(MailEnvelope envelope,
                                                            List<Certificate> availableCertificates,
                                                            CryptoProfile requestedProfile) {
        EncryptionPlan plan = buildEncryptionPlan(envelope, availableCertificates, requestedProfile);
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
                                               CryptoProfile requestedProfile) {
        Map<EmailAddress, List<Certificate>> certificatesByRecipient = new LinkedHashMap<>();
        for (EmailAddress recipient : envelope.getRecipients()) {
            certificatesByRecipient.put(recipient, certificatesForRecipient(recipient, availableCertificates));
        }

        CryptoProfileSelector.EncryptionProfilePlan plan = cryptoProfileSelector.encryptionPlan(
                certificatesByRecipient,
                requestedProfile);
        return plan.success()
                ? EncryptionPlan.ok(plan.profile())
                : EncryptionPlan.failure(plan.failureDetail());
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

    private RoutingDecision routeInbound(MailEnvelope envelope) {
        return new RoutingDecision.PassThrough();
    }

    private record EncryptionPlan(boolean success, CryptoProfile profile, String failureDetail) {
        static EncryptionPlan ok(CryptoProfile profile) {
            return new EncryptionPlan(true, profile, null);
        }

        static EncryptionPlan failure(String detail) {
            return new EncryptionPlan(false, null, detail);
        }
    }
}
