package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.mailsecurity.CryptoProfile;
import com.sealmail.domain.mailsecurity.CryptoProfileSelector;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.mailsecurity.event.MailEncrypted;
import com.sealmail.domain.policy.PreferredAlgorithm;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.mail.pipeline.MailProcessingMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
public class EncryptStep {

    private static final Logger log = LoggerFactory.getLogger(EncryptStep.class);

    private final SMIMEOperations smimeOperations;
    private final CertificateRepository certificateRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final CryptoProfileSelector cryptoProfileSelector;

    public EncryptStep(SMIMEOperations smimeOperations,
                       CertificateRepository certificateRepository,
                       DomainEventPublisher domainEventPublisher) {
        this(smimeOperations, certificateRepository, domainEventPublisher, new CryptoProfileSelector());
    }

    @Autowired
    public EncryptStep(SMIMEOperations smimeOperations,
                       CertificateRepository certificateRepository,
                       DomainEventPublisher domainEventPublisher,
                       CryptoProfileSelector cryptoProfileSelector) {
        this.smimeOperations = smimeOperations;
        this.certificateRepository = certificateRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.cryptoProfileSelector = cryptoProfileSelector;
    }

    public Message<byte[]> execute(Message<byte[]> message) {
        MailProcessingContext context = context(message);
        MailEnvelope envelope = context != null ? context.envelope() : null;
        if (envelope == null) {
            throw new MailProcessingException(
                    MailProcessingErrorType.ENCRYPTION,
                    "Mail processing context not found in message headers",
                    context);
        }

        boolean encryptionEnabled = context.decision().encryptionRequired();
        boolean mustEncrypt = context.decision().mustEncrypt();
        if (!encryptionEnabled && !mustEncrypt) {
            return message;
        }

        try {
            EncryptionPlan plan = buildEncryptionPlan(context);
            if (!plan.success()) {
                return MailProcessingMessages.quarantine(
                        message,
                        "CERTIFICATE_MISSING",
                        mustEncrypt ? "DLP MUST_ENCRYPT: " + plan.failureDetail() : plan.failureDetail(),
                        MailRecordDisposition.EXCEPTION);
            }

            byte[] payload = message.getPayload();
            int originalSize = payload.length;
            List<String> certChain = plan.certificates().values().stream().toList();
            payload = smimeOperations.encryptMultiple(payload, certChain, plan.profile());
            List<MailEncrypted> events = encryptedEvents(envelope, plan.certificates(), plan.thumbprints());
            for (EmailAddress recipient : plan.certificates().keySet()) {
                log.info("  Encrypted for recipient: {} using {}", recipient, plan.profile());
            }

            log.info("=== S/MIME ENCRYPTION COMPLETED ===");
            log.info("  Original size: {} bytes", originalSize);
            log.info("  Encrypted size: {} bytes", payload.length);
            log.info("  First 100 chars: {}", new String(payload).replaceAll("[\r\n]", " ").substring(0, Math.min(100, payload.length)));

            context = context
                    .withCryptoProfile(plan.profile())
                    .withSmimeEncryption(plan.profile().name(), List.copyOf(plan.certificates().keySet()));
            events.forEach(domainEventPublisher::publishEvent);
            return MailProcessingMessages.withPayloadAndContext(message, payload, context);

        } catch (Exception e) {
            log.error("S/MIME encryption failed: {}", e.getMessage(), e);
            throw new MailProcessingException(
                    MailProcessingErrorType.ENCRYPTION,
                    "S/MIME encryption failed: " + e.getMessage(),
                    context,
                    e);
        }
    }

    public String getStepName() {
        return "encrypt";
    }

    private MailProcessingContext context(Message<?> message) {
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }

    private EncryptionPlan buildEncryptionPlan(MailProcessingContext context) {
        Map<EmailAddress, String> selectedCertificates = context.certificateSelection().recipientCertificates();
        if (!selectedCertificates.isEmpty()) {
            CryptoProfile profile = context.cryptoProfile();
            if (profile == null || profile == CryptoProfile.AUTO) {
                profile = CryptoProfile.fromPreferredAlgorithm(context.preferredAlgorithm());
            }
            if (profile == CryptoProfile.AUTO) {
                CryptoProfileSelector.EncryptionProfilePlan plan = profilePlan(
                        context.envelope(),
                        context.preferredAlgorithm());
                if (plan.success()) {
                    profile = plan.profile();
                }
            }
            if (profile == null || profile == CryptoProfile.AUTO) {
                return EncryptionPlan.failure("无法确定邮件加密Profile");
            }
            return EncryptionPlan.success(
                    profile,
                    new LinkedHashMap<>(selectedCertificates),
                    new LinkedHashMap<>(context.certificateSelection().recipientCertificateThumbprints()));
        }

        CryptoProfileSelector.EncryptionProfilePlan plan = profilePlan(
                context.envelope(),
                context.preferredAlgorithm());
        if (!plan.success()) {
            return EncryptionPlan.failure(plan.failureDetail());
        }
        return EncryptionPlan.success(
                plan.profile(),
                plan.certificatePems(),
                plan.certificateThumbprints());
    }

    private CryptoProfileSelector.EncryptionProfilePlan profilePlan(MailEnvelope envelope,
                                                                    PreferredAlgorithm preferredAlgorithm) {
        Map<EmailAddress, List<Certificate>> certificatesByRecipient = new LinkedHashMap<>();
        for (EmailAddress recipient : envelope.getRecipients()) {
            certificatesByRecipient.put(recipient, certificateRepository.findTrustedForEncryption(recipient));
        }
        return cryptoProfileSelector.encryptionPlan(certificatesByRecipient, preferredAlgorithm);
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

    private record EncryptionPlan(boolean success,
                                  CryptoProfile profile,
                                  Map<EmailAddress, String> certificates,
                                  Map<EmailAddress, String> thumbprints,
                                  String failureDetail) {
        static EncryptionPlan success(CryptoProfile profile,
                                      Map<EmailAddress, String> certificates,
                                      Map<EmailAddress, String> thumbprints) {
            return new EncryptionPlan(true, profile, certificates, thumbprints, null);
        }

        static EncryptionPlan failure(String detail) {
            return new EncryptionPlan(false, null, Map.of(), Map.of(), detail);
        }
    }
}
