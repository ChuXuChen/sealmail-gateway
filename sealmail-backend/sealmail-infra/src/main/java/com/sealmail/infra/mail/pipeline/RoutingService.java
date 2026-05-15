package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.mailsecurity.*;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.DomainConfigRepository;
import com.sealmail.domain.policy.PreferredAlgorithm;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.config.properties.PostfixProperties;
import com.sealmail.infra.crypto.util.PemUtils;
import org.slf4j.Logger;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RoutingService {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(RoutingService.class);

    private final MailRouter mailRouter;
    private final DomainConfigRepository domainConfigRepository;
    private final CertificateRepository certificateRepository;
    private final MailProcessingRepository mailProcessingRepository;
    private final PostfixProperties postfixProperties;

    public RoutingService(MailRouter mailRouter,
                           DomainConfigRepository domainConfigRepository,
                           CertificateRepository certificateRepository,
                           MailProcessingRepository mailProcessingRepository,
                           PostfixProperties postfixProperties) {
        this.mailRouter = mailRouter;
        this.domainConfigRepository = domainConfigRepository;
        this.certificateRepository = certificateRepository;
        this.mailProcessingRepository = mailProcessingRepository;
        this.postfixProperties = postfixProperties;
    }

    @Transactional
    public Message<byte[]> routeInbound(Message<byte[]> message) {
        MailEnvelope envelope = (MailEnvelope) message.getHeaders().get("mailEnvelope");
        if (envelope == null) {
            return message;
        }

        Optional<DomainConfig> domainConfig = findFirstActiveLocalRecipientDomain(envelope);
        if (domainConfig.isEmpty()) {
            return exceptionByDomainPolicy(
                    message,
                    envelope,
                    MailDirection.INBOUND,
                    "No recipient domain is configured and enabled as a local domain for this mail");
        }

        List<Certificate> certificates = certificateRepository.findByOwner(envelope.getSender());

        MailProcessing processing = MailProcessing.create(envelope, MailDirection.INBOUND);
        processing.addStep("routing");

        RoutingDecision decision = mailRouter.route(
                envelope,
                MailDirection.INBOUND,
                domainConfig.get(),
                certificates,
                List.of(),
                null
        );

        processing.setRoutingDecision(decision);
        processing.completeStep("routing", true, null);

        mailProcessingRepository.save(processing);

        return enhanceMessageWithRouting(
                message,
                decision,
                envelope,
                domainConfig.get(),
                processing.getId(),
                MailDirection.INBOUND
        );
    }

    @Transactional
    public Message<byte[]> routeOutbound(Message<byte[]> message) {
        MailEnvelope envelope = (MailEnvelope) message.getHeaders().get("mailEnvelope");
        if (envelope == null) {
            return message;
        }

        String senderDomain = envelope.getSender().getDomain();

        Optional<DomainConfig> domainConfig = findActiveLocalDomainConfig(senderDomain);
        if (domainConfig.isEmpty()) {
            return exceptionByDomainPolicy(
                    message,
                    envelope,
                    MailDirection.OUTBOUND,
                    "Sender domain is not configured and enabled as a local domain: " + senderDomain);
        }

        List<Certificate> recipientCerts = envelope.getRecipients().stream()
                .flatMap(recipient -> certificateRepository.findTrustedForEncryption(recipient).stream())
                .toList();

        MailProcessing processing = MailProcessing.create(envelope, MailDirection.OUTBOUND);
        processing.addStep("routing");

        String content = new String(message.getPayload());

        RoutingDecision decision = mailRouter.route(
                envelope,
                MailDirection.OUTBOUND,
                domainConfig.get(),
                recipientCerts,
                List.of(),
                content
        );

        processing.setRoutingDecision(decision);
        processing.completeStep("routing", true, null);

        mailProcessingRepository.save(processing);

        return enhanceMessageWithRouting(
                message,
                decision,
                envelope,
                domainConfig.get(),
                processing.getId(),
                MailDirection.OUTBOUND
        );
    }

    private Optional<DomainConfig> findActiveDomainConfig(String domain) {
        return domainConfigRepository.findByDomain(domain)
                .filter(DomainConfig::isActive);
    }

    private Optional<DomainConfig> findActiveLocalDomainConfig(String domain) {
        return findActiveDomainConfig(domain)
                .filter(DomainConfig::isLocalDomain);
    }

    private Optional<DomainConfig> findFirstActiveLocalRecipientDomain(MailEnvelope envelope) {
        return envelope.getRecipients().stream()
                .map(EmailAddress::getDomain)
                .map(this::findActiveLocalDomainConfig)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Message<byte[]> quarantineByRoutingPolicy(Message<byte[]> message,
                                                       MailEnvelope envelope,
                                                       MailDirection direction,
                                                       String detail) {
        return quarantineByRoutingPolicy(message, envelope, direction, QuarantineReason.POLICY_VIOLATION, detail);
    }

    private Message<byte[]> exceptionByDomainPolicy(Message<byte[]> message,
                                                    MailEnvelope envelope,
                                                    MailDirection direction,
                                                    String detail) {
        return quarantineByRoutingPolicy(message, envelope, direction, QuarantineReason.DOMAIN_NOT_CONFIGURED, detail);
    }

    private Message<byte[]> quarantineByRoutingPolicy(Message<byte[]> message,
                                                       MailEnvelope envelope,
                                                       MailDirection direction,
                                                       QuarantineReason reason,
                                                       String detail) {
        MailProcessing processing = MailProcessing.create(envelope, direction);
        processing.addStep("routing");
        RoutingDecision.Quarantine decision = new RoutingDecision.Quarantine(reason, detail);
        processing.setRoutingDecision(decision);
        processing.completeStep("routing", false, detail);
        mailProcessingRepository.save(processing);
        return enhanceMessageWithRouting(
                message,
                decision,
                envelope,
                null,
                processing.getId(),
                direction
        );
    }

    private Message<byte[]> enhanceMessageWithRouting(Message<byte[]> message, RoutingDecision decision,
                                                       MailEnvelope envelope, DomainConfig domainConfig,
                                                       String processingId, MailDirection direction) {
        Map<String, Object> headers = new HashMap<>();
        headers.putAll(message.getHeaders());
        headers.put("routingDecision", decision);
        headers.put("processingId", processingId);
        headers.put("messageId", envelope.getMessageId());
        headers.put("sender", envelope.getSender());
        headers.put("recipients", envelope.getRecipients());
        headers.put("mailDirection", direction.name());
        headers.put("remoteAddress", envelope.getRemoteHost());
        headers.put("originalMailContent", message.getPayload());

        if (domainConfig != null) {
            headers.putIfAbsent("preferredAlgorithm", domainConfig.getPreferredAlgorithm().name());
            headers.putIfAbsent("dkimEnabled", domainConfig.isDkimEnabled());
        }

        if (direction == MailDirection.OUTBOUND) {
            applyRelayHeaders(headers, MailDirection.OUTBOUND);
            boolean signingEnabled = Boolean.TRUE.equals(message.getHeaders().get("signingEnabled"))
                    || decision instanceof RoutingDecision.OutboundSign
                    || (decision instanceof RoutingDecision.OutboundEncrypt
                    && domainConfig != null
                    && domainConfig.isSigningEnabled());
            if (signingEnabled) {
                headers.put("signingEnabled", true);
                ensureOutboundSigningHeaders(headers, envelope, domainConfig);
            }

            if (decision instanceof RoutingDecision.OutboundEncrypt encrypt) {
                headers.put("encryptionEnabled", true);
                ensureOutboundEncryptionHeaders(headers, encrypt.getRecipients(), domainConfig);
            }
        }

        if (direction == MailDirection.INBOUND) {
            attachInboundCryptoHeaders(headers, envelope);
            applyRelayHeaders(headers, MailDirection.INBOUND);
        }

        if (decision instanceof RoutingDecision.Quarantine quarantine) {
            headers.put("quarantineRequired", true);
            headers.put("quarantineReason", quarantine.getReason().name());
            headers.put("quarantineDetail", quarantine.getDetail());
            headers.put("mailRecordDisposition", MailRecordDisposition.EXCEPTION.name());
        }

        return MessageBuilder.createMessage(message.getPayload(), new MessageHeaders(headers));
    }

    private void ensureOutboundSigningHeaders(Map<String, Object> headers,
                                              MailEnvelope envelope,
                                              DomainConfig domainConfig) {
        if (headers.containsKey("senderCertificate")) {
            return;
        }
        List<Certificate> signingCerts = certificateRepository.findTrustedForSigning(envelope.getSender());
        Certificate selected = selectCertByPreference(
                signingCerts,
                domainConfig != null ? domainConfig.getPreferredAlgorithm() : null
        );
        if (selected != null) {
            headers.put("senderCertificate", selected.getPemContent());
            headers.put("senderCertificateThumbprint", selected.getId().getThumbprint());
        }
    }

    private void ensureOutboundEncryptionHeaders(Map<String, Object> headers,
                                                 List<EmailAddress> recipients,
                                                 DomainConfig domainConfig) {
        if (headers.containsKey("recipientCertificates")) {
            return;
        }
        Map<EmailAddress, String> certMap = new HashMap<>();
        Map<EmailAddress, String> thumbprintMap = new HashMap<>();
        for (EmailAddress recipient : recipients) {
            List<Certificate> certs = certificateRepository.findTrustedForEncryption(recipient);
            Certificate selected = selectCertByPreference(
                    certs,
                    domainConfig != null ? domainConfig.getPreferredAlgorithm() : null
            );
            if (selected != null) {
                certMap.put(recipient, selected.getPemContent());
                thumbprintMap.put(recipient, selected.getId().getThumbprint());
            }
        }
        headers.put("recipientCertificates", certMap);
        headers.put("recipientCertificateThumbprints", thumbprintMap);
    }

    private void attachInboundCryptoHeaders(Map<String, Object> headers, MailEnvelope envelope) {
        if (!headers.containsKey("recipientCertificate")) {
            Certificate decryptionCert = selectInboundDecryptionCertificate(envelope.getRecipients());
            if (decryptionCert != null) {
                headers.put("recipientCertificate", decryptionCert.getPemContent());
                headers.put("recipientCertificateThumbprint", decryptionCert.getId().getThumbprint());
                headers.put("decryptionRequired", true);
            }
        }

        if (!headers.containsKey("senderCertificate")) {
            Certificate senderCert = selectInboundVerificationCertificate(envelope.getSender());
            if (senderCert != null) {
                headers.put("senderCertificate", senderCert.getPemContent());
                headers.put("verificationRequired", true);
            }
        }
    }

    private void applyRelayHeaders(Map<String, Object> headers, MailDirection direction) {
        if (headers.containsKey("relayHost") || !postfixProperties.isEnabled()) {
            return;
        }

        headers.put("relayHost", postfixProperties.getHost());
        headers.put("relayPort", direction == MailDirection.INBOUND
                ? postfixProperties.getAfterFilterPort()
                : postfixProperties.getOutboundPort());
        headers.put("relayUseTls", postfixProperties.isUseTls());
        headers.put("relayUsername", "");
        headers.put("relayPassword", "");
        headers.put("relayTimeout", postfixProperties.getTimeout());
        if (postfixProperties.getEnvelopeFrom() != null && !postfixProperties.getEnvelopeFrom().isBlank()) {
            headers.put("relayEnvelopeFrom", postfixProperties.getEnvelopeFrom());
        }
    }

    private Certificate selectInboundDecryptionCertificate(List<EmailAddress> recipients) {
        for (EmailAddress recipient : recipients) {
            Optional<Certificate> selected = certificateRepository.findTrustedForEncryption(recipient).stream()
                    .filter(Certificate::hasPrivateKey)
                    .findFirst();
            if (selected.isPresent()) {
                return selected.get();
            }
        }
        return null;
    }

    private Certificate selectInboundVerificationCertificate(EmailAddress sender) {
        return certificateRepository.findTrustedForSigning(sender).stream()
                .findFirst()
                .orElse(null);
    }

    private Certificate selectCertByPreference(List<Certificate> certs, PreferredAlgorithm preference) {
        if (certs == null || certs.isEmpty()) {
            return null;
        }
        if (preference == null || preference == PreferredAlgorithm.AUTO) {
            for (Certificate cert : certs) {
                if (isGmCert(cert)) {
                    return cert;
                }
            }
            return certs.get(0);
        }
        if (preference == PreferredAlgorithm.GM_ONLY) {
            for (Certificate cert : certs) {
                if (isGmCert(cert)) {
                    return cert;
                }
            }
            log.warn("GM_ONLY偏好设置下未找到SM2/EC证书");
            return null;
        }
        if (preference == PreferredAlgorithm.STANDARD_ONLY) {
            for (Certificate cert : certs) {
                if (isRsaCert(cert)) {
                    return cert;
                }
            }
            log.warn("STANDARD_ONLY偏好设置下未找到RSA证书");
            return null;
        }
        return certs.get(0);
    }

    private String getCertAlgorithm(Certificate cert) {
        if (cert.getAlgorithm() != null && !cert.getAlgorithm().isBlank()) {
            return cert.getAlgorithm();
        }
        try {
            return PemUtils.parseCertificate(cert.getPemContent()).getPublicKey().getAlgorithm();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private boolean isGmCert(Certificate cert) {
        String alg = getCertAlgorithm(cert);
        return "EC".equals(alg) || "ECDSA".equals(alg) || "SM2".equals(alg);
    }

    private boolean isRsaCert(Certificate cert) {
        return "RSA".equals(getCertAlgorithm(cert));
    }
}
