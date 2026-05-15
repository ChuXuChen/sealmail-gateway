package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateSelector;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.EncryptionPolicy;
import com.sealmail.domain.policy.PolicyPattern;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.shared.model.EmailAddress;

import java.util.ArrayList;
import java.util.List;
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
                        // Continue with encryption required
                        yield createEncryptionDecision(envelope, availableCertificates);
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

        RoutingDecision encryptDecision = createEncryptionDecision(envelope, availableCertificates);

        if (encryptDecision instanceof RoutingDecision.OutboundEncrypt) {
            return encryptDecision;
        }

        if (policy == EncryptionPolicy.MANDATORY) {
            return new RoutingDecision.Quarantine(
                    QuarantineReason.CERTIFICATE_MISSING,
                    "Encryption is mandatory but no certificates available for recipients"
            );
        }

        if (domainConfig.isSigningEnabled()) {
            return new RoutingDecision.OutboundSign(envelope.getRecipients());
        }

        return new RoutingDecision.PassThrough();
    }

    private RoutingDecision createEncryptionDecision(MailEnvelope envelope, List<Certificate> availableCertificates) {
        List<EmailAddress> encryptableRecipients = new ArrayList<>();

        for (EmailAddress recipient : envelope.getRecipients()) {
            Optional<Certificate> cert = certificateSelector.selectForEncryption(recipient, availableCertificates);
            if (cert.isPresent()) {
                encryptableRecipients.add(recipient);
            }
        }

        if (encryptableRecipients.isEmpty()) {
            return new RoutingDecision.PassThrough();
        }

        return new RoutingDecision.OutboundEncrypt(encryptableRecipients);
    }

    private RoutingDecision routeInbound(MailEnvelope envelope) {
        return new RoutingDecision.PassThrough();
    }
}
