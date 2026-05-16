package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.mailsecurity.CertificateSelection;
import com.sealmail.domain.mailsecurity.CryptoProfile;
import com.sealmail.domain.mailsecurity.CryptoProfileSelector;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingDecision;
import com.sealmail.domain.mailsecurity.RoutingDecision;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.shared.model.EmailAddress;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MailCryptoSelectionService {

    private final CertificateRepository certificateRepository;
    private final CryptoProfileSelector cryptoProfileSelector;

    public MailCryptoSelectionService(CertificateRepository certificateRepository,
                                      CryptoProfileSelector cryptoProfileSelector) {
        this.certificateRepository = certificateRepository;
        this.cryptoProfileSelector = cryptoProfileSelector;
    }

    public List<Certificate> inboundRoutingCertificates(MailEnvelope envelope) {
        return certificateRepository.findByOwner(envelope.getSender());
    }

    public List<Certificate> outboundRoutingCertificates(MailEnvelope envelope) {
        return envelope.getRecipients().stream()
                .flatMap(recipient -> certificateRepository.findTrustedForEncryption(recipient).stream())
                .toList();
    }

    public InboundCryptoSelection prepareInbound(CertificateSelection currentCertificates,
                                                 MailEnvelope envelope) {
        CertificateSelection certificates = currentCertificates != null
                ? currentCertificates
                : CertificateSelection.empty();
        boolean decryptionRequired = false;
        boolean verificationRequired = false;

        if (!hasText(certificates.recipientCertificatePem())) {
            Certificate decryptionCert = selectInboundDecryptionCertificate(envelope.getRecipients());
            if (decryptionCert != null) {
                certificates = certificates.withRecipientCertificate(
                        decryptionCert.getPemContent(),
                        decryptionCert.getId().getThumbprint());
                decryptionRequired = true;
            }
        }

        if (!hasText(certificates.senderCertificatePem())) {
            Certificate senderCert = selectInboundVerificationCertificate(envelope.getSender());
            if (senderCert != null) {
                certificates = certificates.withSenderCertificate(
                        senderCert.getPemContent(),
                        senderCert.getId().getThumbprint());
                verificationRequired = true;
            }
        }

        return new InboundCryptoSelection(certificates, decryptionRequired, verificationRequired);
    }

    public OutboundCryptoSelection prepareOutbound(MailEnvelope envelope,
                                                   RoutingDecision routingDecision,
                                                   DomainConfig domainConfig,
                                                   MailProcessingDecision currentDecision,
                                                   CertificateSelection currentCertificates,
                                                   CryptoProfile requestedProfile) {
        MailProcessingDecision decision = currentDecision != null
                ? currentDecision
                : MailProcessingDecision.none();
        CertificateSelection certificates = currentCertificates != null
                ? currentCertificates
                : CertificateSelection.empty();
        CryptoProfile profile = requestedProfile != null ? requestedProfile : CryptoProfile.AUTO;

        if (routingDecision instanceof RoutingDecision.Quarantine) {
            return new OutboundCryptoSelection(decision, certificates, profile);
        }

        boolean encryptionRequired = routingDecision instanceof RoutingDecision.OutboundEncrypt;
        boolean mustEncrypt = decision.mustEncrypt();
        boolean signingRequired = decision.signingRequired()
                || routingDecision instanceof RoutingDecision.OutboundSign
                || (encryptionRequired && domainConfig != null && domainConfig.isSigningEnabled());

        if (encryptionRequired || mustEncrypt) {
            decision = decision.withEncryptionRequired(true);
        }
        if (signingRequired) {
            decision = decision.withSigningRequired(true);
        }

        CryptoProfileSelector.EncryptionProfilePlan encryptionPlan =
                selectOutboundEncryptionPlan(envelope, encryptionRecipients(envelope, routingDecision), profile);
        if (encryptionPlan.success()) {
            if (certificates.recipientCertificates().isEmpty()) {
                certificates = certificates.withRecipientCertificates(
                        encryptionPlan.certificatePems(),
                        encryptionPlan.certificateThumbprints());
            }
            if (!profile.isConcrete()) {
                profile = encryptionPlan.profile();
            }
        }

        if (signingRequired && !hasText(certificates.senderCertificatePem())) {
            List<Certificate> signingCerts = certificateRepository.findTrustedForSigning(envelope.getSender());
            Certificate selected = cryptoProfileSelector.select(signingCerts, profile).orElse(null);
            if (selected != null) {
                certificates = certificates.withSenderCertificate(
                        selected.getPemContent(),
                        selected.getId().getThumbprint());
                if (!profile.isConcrete()) {
                    profile = cryptoProfileSelector.profileOf(selected).orElse(CryptoProfile.AUTO);
                }
            }
        }

        return new OutboundCryptoSelection(decision, certificates, profile);
    }

    private CryptoProfileSelector.EncryptionProfilePlan selectOutboundEncryptionPlan(MailEnvelope envelope,
                                                                                    List<EmailAddress> recipients,
                                                                                    CryptoProfile profile) {
        Map<EmailAddress, List<Certificate>> certificatesByRecipient = new LinkedHashMap<>();
        for (EmailAddress recipient : recipients) {
            certificatesByRecipient.put(recipient, certificateRepository.findTrustedForEncryption(recipient));
        }
        return cryptoProfileSelector.encryptionPlan(certificatesByRecipient, profile);
    }

    private List<EmailAddress> encryptionRecipients(MailEnvelope envelope, RoutingDecision routingDecision) {
        if (routingDecision instanceof RoutingDecision.OutboundEncrypt outboundEncrypt) {
            return outboundEncrypt.getRecipients();
        }
        return envelope.getRecipients();
    }

    private Certificate selectInboundDecryptionCertificate(List<EmailAddress> recipients) {
        for (EmailAddress recipient : recipients) {
            Certificate selected = certificateRepository.findTrustedForEncryption(recipient).stream()
                    .filter(Certificate::hasPrivateKey)
                    .findFirst()
                    .orElse(null);
            if (selected != null) {
                return selected;
            }
        }
        return null;
    }

    private Certificate selectInboundVerificationCertificate(EmailAddress sender) {
        return certificateRepository.findTrustedForSigning(sender).stream()
                .findFirst()
                .orElse(null);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record InboundCryptoSelection(
            CertificateSelection certificates,
            boolean decryptionRequired,
            boolean verificationRequired
    ) {
    }

    public record OutboundCryptoSelection(
            MailProcessingDecision decision,
            CertificateSelection certificates,
            CryptoProfile cryptoProfile
    ) {
    }
}
