package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.event.MailDecrypted;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.mail.pipeline.MailPipelineStep;
import com.sealmail.infra.mail.pipeline.PipelineResult;
import com.sealmail.infra.crypto.KeyStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Inbound pipeline step: Decrypt S/MIME encrypted mail.
 */
@Component
public class DecryptStep implements MailPipelineStep {

    private static final Logger log = LoggerFactory.getLogger(DecryptStep.class);

    private final SMIMEOperations smimeOperations;
    private final KeyStoreService keyStoreService;
    private final CertificateRepository certificateRepository;

    public DecryptStep(SMIMEOperations smimeOperations,
                       KeyStoreService keyStoreService,
                       CertificateRepository certificateRepository) {
        this.smimeOperations = smimeOperations;
        this.keyStoreService = keyStoreService;
        this.certificateRepository = certificateRepository;
    }

    @Override
    public PipelineResult execute(Message<byte[]> message) {
        MailProcessingContext context = context(message);
        MailEnvelope envelope = context != null ? context.envelope() : null;
        if (envelope == null) {
            throw new MailProcessingException(
                    MailProcessingErrorType.DECRYPTION,
                    "Mail processing context not found in message headers",
                    context);
        }

        try {
            if (!smimeOperations.isEncrypted(message.getPayload())) {
                return PipelineResult.success(message.getPayload());
            }

            String recipientCert = context.certificateSelection().recipientCertificatePem();
            String privateKey = null;

            String thumbprint = context.certificateSelection().recipientCertificateThumbprint();
            if (thumbprint != null && !thumbprint.isBlank()) {
                var certOpt = certificateRepository.findById(new CertificateId(thumbprint));
                if (certOpt.isPresent() && certOpt.get().hasPrivateKey()) {
                    privateKey = certOpt.get().getPrivateKeyData();
                    log.info("使用证书关联私钥进行解密: thumbprint={}", thumbprint);
                }
            }

            if (privateKey == null) {
                for (var recipient : envelope.getRecipients()) {
                    privateKey = keyStoreService.getPrivateKeyPem(recipient);
                    if (privateKey != null) {
                        log.info("从 KeyStore 加载私钥进行解密: recipient={}", recipient);
                        break;
                    }
                }
            }

            if (recipientCert == null || privateKey == null) {
                return PipelineResult.quarantine(
                        message.getPayload(),
                        "DECRYPTION_FAILED",
                        "Encrypted S/MIME mail cannot be decrypted: missing recipient certificate/private key"
                );
            }

            byte[] decrypted = smimeOperations.decrypt(message.getPayload(), privateKey, recipientCert);
            EmailAddress recipient = envelope.getRecipients().isEmpty() ? null : envelope.getRecipients().getFirst();
            if (recipient != null) {
                return PipelineResult.success(decrypted, new MailDecrypted(envelope.getMessageId(), recipient));
            }
            return PipelineResult.success(decrypted);

        } catch (Exception e) {
            throw new MailProcessingException(
                    MailProcessingErrorType.DECRYPTION,
                    "Decryption failed: " + e.getMessage(),
                    context,
                    e);
        }
    }

    @Override
    public String getStepName() {
        return "decrypt";
    }

    private MailProcessingContext context(Message<?> message) {
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }
}
