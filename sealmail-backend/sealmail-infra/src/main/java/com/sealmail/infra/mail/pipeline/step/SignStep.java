package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.mailsecurity.CryptoProfile;
import com.sealmail.domain.mailsecurity.CryptoProfileSelector;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.event.MailSigned;
import com.sealmail.infra.crypto.KeyStoreService;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.mail.pipeline.MailProcessingMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Outbound pipeline step: Sign outgoing mail with S/MIME.
 */
@Component
public class SignStep {

    private static final Logger log = LoggerFactory.getLogger(SignStep.class);

    private final SMIMEOperations smimeOperations;
    private final KeyStoreService keyStoreService;
    private final CertificateRepository certificateRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final CryptoProfileSelector cryptoProfileSelector;

    public SignStep(SMIMEOperations smimeOperations,
                    KeyStoreService keyStoreService,
                    CertificateRepository certificateRepository,
                    DomainEventPublisher domainEventPublisher) {
        this(smimeOperations, keyStoreService, certificateRepository, domainEventPublisher, new CryptoProfileSelector());
    }

    @Autowired
    public SignStep(SMIMEOperations smimeOperations,
                    KeyStoreService keyStoreService,
                    CertificateRepository certificateRepository,
                    DomainEventPublisher domainEventPublisher,
                    CryptoProfileSelector cryptoProfileSelector) {
        this.smimeOperations = smimeOperations;
        this.keyStoreService = keyStoreService;
        this.certificateRepository = certificateRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.cryptoProfileSelector = cryptoProfileSelector;
    }

    public Message<byte[]> execute(Message<byte[]> message) {
        MailProcessingContext context = context(message);
        MailEnvelope envelope = context != null ? context.envelope() : null;
        if (envelope == null) {
            return message;
        }

        if (!context.decision().signingRequired()) {
            return message;
        }

        try {
            String senderCert = context.certificateSelection().senderCertificatePem();
            String privateKey = null;

            String thumbprint = context.certificateSelection().senderCertificateThumbprint();
            CryptoProfile profile = context.cryptoProfile();
            if (profile == null || profile == CryptoProfile.AUTO) {
                profile = CryptoProfile.fromPreferredAlgorithm(context.preferredAlgorithm());
            }

            if (senderCert == null) {
                Certificate selected = cryptoProfileSelector
                        .select(certificateRepository.findTrustedForSigning(envelope.getSender()), profile)
                        .orElse(null);
                if (selected != null) {
                    senderCert = selected.getPemContent();
                    thumbprint = selected.getId().getThumbprint();
                    cryptoProfileSelector.profileOf(selected).ifPresent(selectedProfile -> {
                        log.info("选择 {} profile 证书用于签名", selectedProfile);
                    });
                }
            }

            if (senderCert == null) {
                if (profile != null && profile.isConcrete()) {
                    throw new MailProcessingException(
                            MailProcessingErrorType.SIGNING,
                            profile + " profile 策略：未找到匹配签名证书",
                            context);
                }
                log.info("Signing skipped: no sender certificate found");
                return message;
            }

            // 根据证书算法加载对应私钥：优先证书关联私钥，其次 KeyStore。
            if (privateKey == null) {
                if (thumbprint != null && !thumbprint.isBlank()) {
                    var certOpt = certificateRepository.findById(
                            new com.sealmail.domain.certificate.CertificateId(thumbprint));
                    if (certOpt.isPresent() && certOpt.get().hasPrivateKey()) {
                        privateKey = certOpt.get().getPrivateKeyData();
                        log.info("使用证书关联私钥进行签名: thumbprint={}", thumbprint);
                    }
                }
            }

            if (privateKey == null) {
                privateKey = keyStoreService.getPrivateKeyPem(envelope.getSender());
                if (privateKey != null) {
                    log.info("从 KeyStore 加载私钥进行签名: sender={}", envelope.getSender());
                }
            }

            if (privateKey == null) {
                log.info("Signing skipped: private key not found for selected certificate");
                return message;
            }

            byte[] original = message.getPayload();
            byte[] signed = smimeOperations.sign(original, privateKey, senderCert);

            log.info("=== S/MIME SIGNING COMPLETED ===");
            log.info("  Original size: {} bytes", original.length);
            log.info("  Signed size: {} bytes", signed.length);
            log.info("  First 100 chars: {}", new String(signed).replaceAll("[\r\n]", " ").substring(0, Math.min(100, signed.length)));

            if (thumbprint != null && !thumbprint.isBlank()) {
                domainEventPublisher.publishEvent(new MailSigned(
                        envelope.getMessageId(),
                        envelope.getSender(),
                        new com.sealmail.domain.certificate.CertificateId(thumbprint)));
                return MailProcessingMessages.withPayload(message, signed);
            }
            return MailProcessingMessages.withPayload(message, signed);

        } catch (Exception e) {
            if (e instanceof MailProcessingException mailProcessingException) {
                throw mailProcessingException;
            }
            log.error("S/MIME signing failed: {}", e.getMessage(), e);
            throw new MailProcessingException(
                    MailProcessingErrorType.SIGNING,
                    "S/MIME signing failed: " + e.getMessage(),
                    context,
                    e);
        }
    }

    public String getStepName() {
        return "sign";
    }

    private MailProcessingContext context(Message<?> message) {
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }
}
