package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.event.MailSigned;
import com.sealmail.domain.policy.PreferredAlgorithm;
import com.sealmail.infra.crypto.KeyStoreService;
import com.sealmail.infra.crypto.util.PemUtils;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.mail.pipeline.MailPipelineStep;
import com.sealmail.infra.mail.pipeline.PipelineResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Outbound pipeline step: Sign outgoing mail with S/MIME.
 */
@Component
public class SignStep implements MailPipelineStep {

    private static final Logger log = LoggerFactory.getLogger(SignStep.class);

    private final SMIMEOperations smimeOperations;
    private final KeyStoreService keyStoreService;
    private final CertificateRepository certificateRepository;

    public SignStep(SMIMEOperations smimeOperations,
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
            return PipelineResult.success(message.getPayload());
        }

        if (!context.decision().signingRequired()) {
            return PipelineResult.success(message.getPayload());
        }

        try {
            String senderCert = context.certificateSelection().senderCertificatePem();
            String privateKey = null;

            PreferredAlgorithm preference = context.preferredAlgorithm();
            String thumbprint = context.certificateSelection().senderCertificateThumbprint();

            // 根据算法偏好选择证书（RoutingService已预筛选，此处作为后备）
            if (senderCert == null) {
                var certs = certificateRepository.findTrustedForSigning(envelope.getSender());
                for (var cert : certs) {
                    try {
                        String alg = PemUtils.parseCertificate(cert.getPemContent()).getPublicKey().getAlgorithm();
                        boolean isGm = "EC".equals(alg) || "ECDSA".equals(alg);
                        boolean isStandard = "RSA".equals(alg);

                        if (preference == PreferredAlgorithm.GM_ONLY && isGm) {
                            senderCert = cert.getPemContent();
                            thumbprint = cert.getId().getThumbprint();
                            log.info("选择SM2证书用于国密签名");
                            break;
                        } else if (preference == PreferredAlgorithm.STANDARD_ONLY && isStandard) {
                            senderCert = cert.getPemContent();
                            thumbprint = cert.getId().getThumbprint();
                            log.info("选择RSA证书用于标准签名");
                            break;
                        } else if (preference == PreferredAlgorithm.AUTO && isGm) {
                            senderCert = cert.getPemContent();
                            thumbprint = cert.getId().getThumbprint();
                            log.info("选择SM2证书用于国密签名");
                            break;
                        }
                    } catch (Exception e) {
                        // skip
                    }
                }
                // AUTO回退：找RSA
                if (senderCert == null && preference == PreferredAlgorithm.AUTO) {
                    for (var cert : certs) {
                        try {
                            String alg = PemUtils.parseCertificate(cert.getPemContent()).getPublicKey().getAlgorithm();
                            if ("RSA".equals(alg)) {
                                senderCert = cert.getPemContent();
                                thumbprint = cert.getId().getThumbprint();
                                log.info("选择RSA证书用于标准签名");
                                break;
                            }
                        } catch (Exception e) {
                            // skip
                        }
                    }
                }
            }

            if (senderCert == null) {
                if (preference == PreferredAlgorithm.GM_ONLY) {
                    throw new MailProcessingException(
                            MailProcessingErrorType.SIGNING,
                            "GM_ONLY策略：未找到SM2签名证书",
                            context);
                }
                if (preference == PreferredAlgorithm.STANDARD_ONLY) {
                    throw new MailProcessingException(
                            MailProcessingErrorType.SIGNING,
                            "STANDARD_ONLY策略：未找到RSA签名证书",
                            context);
                }
                log.info("Signing skipped: no sender certificate found");
                return PipelineResult.success(message.getPayload());
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
                log.info("Signing skipped: privateKey not found for cert algorithm");
                return PipelineResult.success(message.getPayload());
            }

            byte[] original = message.getPayload();
            byte[] signed = smimeOperations.sign(original, privateKey, senderCert);

            log.info("=== S/MIME SIGNING COMPLETED ===");
            log.info("  Original size: {} bytes", original.length);
            log.info("  Signed size: {} bytes", signed.length);
            log.info("  First 100 chars: {}", new String(signed).replaceAll("[\r\n]", " ").substring(0, Math.min(100, signed.length)));

            if (thumbprint != null && !thumbprint.isBlank()) {
                return PipelineResult.success(signed, new MailSigned(
                        envelope.getMessageId(),
                        envelope.getSender(),
                        new com.sealmail.domain.certificate.CertificateId(thumbprint)));
            }
            return PipelineResult.success(signed);

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

    private String resolveAlgorithm(String certPem) {
        try {
            return PemUtils.parseCertificate(certPem).getPublicKey().getAlgorithm();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    @Override
    public String getStepName() {
        return "sign";
    }

    private MailProcessingContext context(Message<?> message) {
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }
}
