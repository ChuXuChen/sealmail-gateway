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
        MailProcessingContext messageContext = context(message);
        MailEnvelope envelope = messageContext != null ? messageContext.envelope() : null;
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
        MailProcessingContext messageContext = context(message);
        MailEnvelope envelope = messageContext != null ? messageContext.envelope() : null;
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
        MailProcessingContext baseContext = context(message);
        if (baseContext == null) {
            baseContext = MailProcessingContext.initial(
                    envelope,
                    direction,
                    null,
                    message.getPayload(),
                    null,
                    envelope.getRemoteHost());
        }
        PreferredAlgorithm preferredAlgorithm = baseContext.preferredAlgorithm() != PreferredAlgorithm.AUTO
                ? baseContext.preferredAlgorithm()
                : domainConfig != null ? domainConfig.getPreferredAlgorithm() : PreferredAlgorithm.AUTO;
        MailProcessingDecision processingDecision = baseContext.decision();
        CertificateSelection certificates = baseContext.certificateSelection();
        RelayProfile relayProfile = relayProfile(direction);

        if (direction == MailDirection.OUTBOUND) {
            boolean signingEnabled = processingDecision.signingRequired()
                    || decision instanceof RoutingDecision.OutboundSign
                    || (decision instanceof RoutingDecision.OutboundEncrypt
                    && domainConfig != null
                    && domainConfig.isSigningEnabled());
            if (signingEnabled) {
                processingDecision = processingDecision.withSigningRequired(true);
                certificates = ensureOutboundSigningCertificates(certificates, envelope, preferredAlgorithm);
            }

            if (decision instanceof RoutingDecision.OutboundEncrypt encrypt) {
                processingDecision = processingDecision.withEncryptionRequired(true);
                certificates = ensureOutboundEncryptionCertificates(
                        certificates,
                        encrypt.getRecipients(),
                        preferredAlgorithm);
            }
        }

        if (direction == MailDirection.INBOUND) {
            InboundCryptoSelection inboundCrypto = attachInboundCrypto(certificates, envelope);
            certificates = inboundCrypto.certificates();
            processingDecision = processingDecision
                    .withDecryptionRequired(inboundCrypto.decryptionRequired())
                    .withVerificationRequired(inboundCrypto.verificationRequired());
        }

        if (decision instanceof RoutingDecision.Quarantine quarantine) {
            processingDecision = processingDecision.withQuarantine(quarantine.getReason(), quarantine.getDetail());
            baseContext = baseContext.withRecordDisposition(MailRecordDisposition.EXCEPTION);
        }

        if (domainConfig != null && domainConfig.isDkimEnabled()) {
            processingDecision = processingDecision.withDkimSigningRequired(true);
        }

        MailProcessingContext context = baseContext
                .withRoutingDecision(decision)
                .withProcessingId(processingId)
                .withDirection(direction)
                .withPreferredAlgorithm(preferredAlgorithm)
                .withDecision(processingDecision)
                .withCertificateSelection(certificates)
                .withRelayProfile(relayProfile);
        AuditTrace auditTrace = context.auditTrace();
        if (auditTrace != null) {
            context = context.withAuditTrace(new AuditTrace(
                    processingId,
                    auditTrace.correlationId(),
                    auditTrace.messageId(),
                    auditTrace.submissionType(),
                    auditTrace.remoteAddress()));
        }
        return MessageBuilder.withPayload(message.getPayload())
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }

    private MailProcessingContext context(Message<?> message) {
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }

    private CertificateSelection ensureOutboundSigningCertificates(CertificateSelection certificates,
                                                                   MailEnvelope envelope,
                                                                   PreferredAlgorithm preferredAlgorithm) {
        if (hasText(certificates.senderCertificatePem())) {
            return certificates;
        }
        List<Certificate> signingCerts = certificateRepository.findTrustedForSigning(envelope.getSender());
        Certificate selected = selectCertByPreference(
                signingCerts,
                preferredAlgorithm
        );
        if (selected != null) {
            return certificates.withSenderCertificate(selected.getPemContent(), selected.getId().getThumbprint());
        }
        return certificates;
    }

    private CertificateSelection ensureOutboundEncryptionCertificates(CertificateSelection certificates,
                                                                     List<EmailAddress> recipients,
                                                                     PreferredAlgorithm preferredAlgorithm) {
        if (!certificates.recipientCertificates().isEmpty()) {
            return certificates;
        }
        Map<EmailAddress, String> certMap = new HashMap<>();
        Map<EmailAddress, String> thumbprintMap = new HashMap<>();
        for (EmailAddress recipient : recipients) {
            List<Certificate> certs = certificateRepository.findTrustedForEncryption(recipient);
            Certificate selected = selectCertByPreference(
                    certs,
                    preferredAlgorithm
            );
            if (selected != null) {
                certMap.put(recipient, selected.getPemContent());
                thumbprintMap.put(recipient, selected.getId().getThumbprint());
            }
        }
        return certificates.withRecipientCertificates(certMap, thumbprintMap);
    }

    private InboundCryptoSelection attachInboundCrypto(CertificateSelection certificates, MailEnvelope envelope) {
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

    private RelayProfile relayProfile(MailDirection direction) {
        if (!postfixProperties.isEnabled()) {
            return null;
        }
        return new RelayProfile(
                postfixProperties.getHost(),
                direction == MailDirection.INBOUND
                        ? postfixProperties.getAfterFilterPort()
                        : postfixProperties.getOutboundPort(),
                postfixProperties.isUseTls(),
                "",
                "",
                postfixProperties.getTimeout(),
                postfixProperties.getEnvelopeFrom());
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
        String curveOid = getCertCurveOid(cert);
        if (curveOid != null) {
            return "1.2.156.10197.1.301".equals(curveOid);
        }
        return "SM2".equals(getCertAlgorithm(cert));
    }

    private boolean isRsaCert(Certificate cert) {
        return "RSA".equals(getCertAlgorithm(cert));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String getCertCurveOid(Certificate cert) {
        try {
            java.security.cert.X509Certificate x509 = PemUtils.parseCertificate(cert.getPemContent());
            org.bouncycastle.cert.X509CertificateHolder holder =
                    new org.bouncycastle.cert.X509CertificateHolder(x509.getEncoded());
            Object parameters = holder.getSubjectPublicKeyInfo().getAlgorithm().getParameters();
            return parameters != null ? parameters.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private record InboundCryptoSelection(
            CertificateSelection certificates,
            boolean decryptionRequired,
            boolean verificationRequired
    ) {
    }
}
