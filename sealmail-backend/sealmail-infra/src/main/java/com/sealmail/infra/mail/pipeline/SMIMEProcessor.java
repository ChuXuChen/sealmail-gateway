package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.certificate.spi.SignatureValidationResult;
import com.sealmail.domain.certificate.spi.SMIMEEncryptionSuite;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.persistence.entity.CertificateEntity;
import com.sealmail.infra.persistence.repository.CertificateRepositoryImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * S/MIME 邮件处理器 - 集成到邮件处理流水线
 * 负责：
 * 1. 入站邮件：解密、验证签名、检测恶意内容
 * 2. 出站邮件：签名、加密
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SMIMEProcessor {

    private final SMIMEOperations smimeOperations;
    private final CertificateRepositoryImpl certificateRepository;

    /**
     * 处理入站邮件
     * @return 处理结果
     */
    public ProcessingResult processInbound(MailEnvelope envelope) {
        byte[] content = envelope.getRawContent();
        ProcessingResult result = new ProcessingResult();
        result.setOriginalContent(content);

        // 1. 检查是否已加密
        if (smimeOperations.isEncrypted(content)) {
            result.setEncrypted(true);
            log.info("检测到加密邮件，发件人: {}, 收件人: {}", envelope.getSender(), envelope.getRecipients());

            // 尝试查找收件人私钥进行解密
            Optional<DecryptionResult> decrypted = tryDecrypt(envelope);
            if (decrypted.isPresent()) {
                result.setDecrypted(true);
                result.setProcessedContent(decrypted.get().content());
                content = decrypted.get().content();
                result.addMessage("邮件已使用证书解密: " + decrypted.get().certificateId());
            } else {
                result.setDecryptionFailed(true);
                result.addWarning("邮件已加密但无法解密：未找到匹配的私钥");
            }
        }

        // 2. 检查是否已签名
        if (smimeOperations.isSigned(content)) {
            result.setSigned(true);
            log.info("检测到签名邮件，发件人: {}", envelope.getSender());

            // 尝试验证签名
            Optional<SignatureValidationResult> validation = tryVerifySignature(envelope, content);
            if (validation.isPresent()) {
                SignatureValidationResult svr = validation.get();
                result.setSignatureValid(svr.isValid());
                result.setSignatureTrusted(svr.isTrusted());
                result.setSignerEmail(svr.getSignerEmail());

                if (svr.isValid()) {
                    result.addMessage("签名验证通过: " + svr.getSigner());
                    if (svr.isCertificateExpired()) {
                        result.addWarning("签名证书已过期");
                    }
                } else {
                    result.addWarning("签名验证失败: " + String.join(", ", svr.getValidationErrors()));
                }

                // 提取签名后的原始内容
                try {
                    byte[] signedContent = smimeOperations.extractSignedContent(content);
                    if (signedContent != null && signedContent.length > 0) {
                        result.setProcessedContent(signedContent);
                    }
                } catch (Exception e) {
                    log.warn("提取签名邮件内容失败", e);
                }
            }
        }

        // 如果处理后没有内容，使用原始内容
        if (result.getProcessedContent() == null) {
            result.setProcessedContent(content);
        }

        return result;
    }

    /**
     * 处理出站邮件
     */
    public ProcessingResult processOutbound(MailEnvelope envelope, boolean sign, boolean encrypt) {
        byte[] content = envelope.getRawContent();
        ProcessingResult result = new ProcessingResult();
        result.setOriginalContent(content);

        // 1. 签名（如需要）
        if (sign) {
            Optional<CertificateEntity> signingCert = findSigningCertificate(envelope.getSender().getValue());
            if (signingCert.isPresent()) {
                try {
                    byte[] signed = smimeOperations.sign(
                            content,
                            signingCert.get().getPrivateKeyData(),
                            signingCert.get().getPemContent()
                    );
                    content = signed;
                    result.setSigned(true);
                    result.addMessage("邮件已使用证书签名: " + signingCert.get().getThumbprint());
                } catch (Exception e) {
                    log.error("邮件签名失败", e);
                    result.addWarning("签名失败: " + e.getMessage());
                }
            } else {
                result.addWarning("未找到发件人签名证书: " + envelope.getSender().getValue());
            }
        }

        // 2. 加密（如需要）
        if (encrypt) {
            try {
                EncryptionPlan plan = buildEncryptionPlan(envelope);
                if (!plan.success()) {
                    result.addWarning("加密失败: " + plan.failureDetail());
                } else {
                    byte[] encrypted = smimeOperations.encryptMultiple(content, plan.certificates(), plan.suite());
                    content = encrypted;
                    result.setEncrypted(true);
                    result.addMessage("邮件已加密，收件人数量: " + plan.certificates().size());
                }
            } catch (Exception e) {
                log.error("邮件加密失败", e);
                result.addWarning("加密失败: " + e.getMessage());
            }
        }

        result.setProcessedContent(content);
        return result;
    }

    private Optional<DecryptionResult> tryDecrypt(MailEnvelope envelope) {
        // 查找收件人关联的私钥
        for (var recipient : envelope.getRecipients()) {
            String email = recipient.getValue();
            List<CertificateEntity> certs = certificateRepository.findByOwnerEmail(email);
            for (CertificateEntity cert : certs) {
                if (cert.getPrivateKeyData() != null && !cert.isRevoked() && cert.isTrusted()) {
                    try {
                        byte[] decrypted = smimeOperations.decrypt(
                                envelope.getRawContent(),
                                cert.getPrivateKeyData(),
                                cert.getPemContent()
                        );
                        return Optional.of(new DecryptionResult(decrypted, cert.getThumbprint()));
                    } catch (Exception ignored) {
                        // 尝试下一个证书
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<SignatureValidationResult> tryVerifySignature(MailEnvelope envelope, byte[] content) {
        // 查找发件人证书验证签名
        String senderEmail = envelope.getSender().getValue();
        List<CertificateEntity> senderCerts = certificateRepository.findByOwnerEmail(senderEmail);
        for (CertificateEntity cert : senderCerts) {
            if (!cert.isRevoked()) {
                try {
                    SignatureValidationResult result = smimeOperations.verifySignatureDetail(
                            content,
                            cert.getPemContent()
                    );
                    return Optional.of(result);
                } catch (Exception ignored) {
                }
            }
        }
        return Optional.empty();
    }

    private Optional<CertificateEntity> findSigningCertificate(String sender) {
        List<CertificateEntity> certs = certificateRepository.findSigningCertificates(sender);
        return certs.stream().findFirst();
    }

    private EncryptionPlan buildEncryptionPlan(MailEnvelope envelope) {
        List<RecipientCertificateOptions> recipientOptions = new ArrayList<>();
        for (EmailAddress recipient : envelope.getRecipients()) {
            List<CertificateEntity> certificates = certificateRepository.findByOwnerEmail(recipient.getValue())
                    .stream()
                    .filter(c -> c.isTrusted() && !c.isRevoked())
                    .toList();
            recipientOptions.add(new RecipientCertificateOptions(
                    recipient,
                    selectGmCertificate(certificates),
                    selectStandardCertificate(certificates)));
        }

        List<String> recipientsWithoutCertificates = recipientOptions.stream()
                .filter(option -> !option.supportsGm() && !option.supportsStandard())
                .map(option -> option.recipient().getValue())
                .toList();
        if (!recipientsWithoutCertificates.isEmpty()) {
            if (recipientsWithoutCertificates.size() == envelope.getRecipients().size()) {
                return EncryptionPlan.failure("未找到收件人加密证书");
            }
            return EncryptionPlan.failure("以下收件人没有加密证书: "
                    + String.join(", ", recipientsWithoutCertificates));
        }

        if (recipientOptions.stream().allMatch(RecipientCertificateOptions::supportsGm)) {
            return EncryptionPlan.success(
                    SMIMEEncryptionSuite.GM,
                    recipientOptions.stream().map(RecipientCertificateOptions::gmCertificate).toList());
        }
        if (recipientOptions.stream().allMatch(RecipientCertificateOptions::supportsStandard)) {
            return EncryptionPlan.success(
                    SMIMEEncryptionSuite.STANDARD,
                    recipientOptions.stream().map(RecipientCertificateOptions::standardCertificate).toList());
        }
        return EncryptionPlan.failure("多收件人无法共享同一加密策略，需所有收件人同时具备SM2或RSA加密证书: "
                + capabilitySummary(recipientOptions));
    }

    private String selectGmCertificate(List<CertificateEntity> certificates) {
        return certificates.stream()
                .filter(this::isGmCertificate)
                .map(CertificateEntity::getPemContent)
                .findFirst()
                .orElse(null);
    }

    private String selectStandardCertificate(List<CertificateEntity> certificates) {
        return certificates.stream()
                .filter(this::isRsaCertificate)
                .map(CertificateEntity::getPemContent)
                .findFirst()
                .orElse(null);
    }

    private boolean isRsaCertificate(CertificateEntity certificate) {
        return "RSA".equals(certificateAlgorithm(certificate));
    }

    private boolean isGmCertificate(CertificateEntity certificate) {
        String algorithm = certificateAlgorithm(certificate);
        return "SM2".equals(algorithm) || "EC".equals(algorithm) || "ECDSA".equals(algorithm);
    }

    private String certificateAlgorithm(CertificateEntity certificate) {
        if (certificate.getAlgorithm() != null && !certificate.getAlgorithm().isBlank()) {
            return certificate.getAlgorithm().trim().toUpperCase(Locale.ROOT);
        }
        try {
            X509Certificate parsed = com.sealmail.infra.crypto.util.PemUtils.parseCertificate(certificate.getPemContent());
            return parsed.getPublicKey().getAlgorithm().trim().toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private String capabilitySummary(List<RecipientCertificateOptions> recipientOptions) {
        return recipientOptions.stream()
                .map(option -> option.recipient().getValue() + "=" + option.capabilityLabel())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    // ============ 内部类 ============

    private record DecryptionResult(byte[] content, String certificateId) {}

    private record RecipientCertificateOptions(EmailAddress recipient,
                                               String gmCertificate,
                                               String standardCertificate) {
        boolean supportsGm() {
            return gmCertificate != null;
        }

        boolean supportsStandard() {
            return standardCertificate != null;
        }

        String capabilityLabel() {
            if (supportsGm() && supportsStandard()) {
                return "GM,STANDARD";
            }
            if (supportsGm()) {
                return "GM";
            }
            if (supportsStandard()) {
                return "STANDARD";
            }
            return "NONE";
        }
    }

    private record EncryptionPlan(boolean success,
                                  SMIMEEncryptionSuite suite,
                                  List<String> certificates,
                                  String failureDetail) {
        static EncryptionPlan success(SMIMEEncryptionSuite suite, List<String> certificates) {
            return new EncryptionPlan(true, suite, certificates, null);
        }

        static EncryptionPlan failure(String detail) {
            return new EncryptionPlan(false, null, List.of(), detail);
        }
    }

    public static class ProcessingResult {
        private byte[] originalContent;
        private byte[] processedContent;
        private boolean encrypted;
        private boolean decrypted;
        private boolean decryptionFailed;
        private boolean signed;
        private boolean signatureValid;
        private boolean signatureTrusted;
        private String signerEmail;
        private List<String> messages = new java.util.ArrayList<>();
        private List<String> warnings = new java.util.ArrayList<>();

        public void addMessage(String msg) {
            messages.add(msg);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        // Getters & Setters
        public byte[] getOriginalContent() { return originalContent; }
        public void setOriginalContent(byte[] originalContent) { this.originalContent = originalContent; }
        public byte[] getProcessedContent() { return processedContent; }
        public void setProcessedContent(byte[] processedContent) { this.processedContent = processedContent; }
        public boolean isEncrypted() { return encrypted; }
        public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }
        public boolean isDecrypted() { return decrypted; }
        public void setDecrypted(boolean decrypted) { this.decrypted = decrypted; }
        public boolean isDecryptionFailed() { return decryptionFailed; }
        public void setDecryptionFailed(boolean decryptionFailed) { this.decryptionFailed = decryptionFailed; }
        public boolean isSigned() { return signed; }
        public void setSigned(boolean signed) { this.signed = signed; }
        public boolean isSignatureValid() { return signatureValid; }
        public void setSignatureValid(boolean signatureValid) { this.signatureValid = signatureValid; }
        public boolean isSignatureTrusted() { return signatureTrusted; }
        public void setSignatureTrusted(boolean signatureTrusted) { this.signatureTrusted = signatureTrusted; }
        public String getSignerEmail() { return signerEmail; }
        public void setSignerEmail(String signerEmail) { this.signerEmail = signerEmail; }
        public List<String> getMessages() { return messages; }
        public List<String> getWarnings() { return warnings; }
    }
}
