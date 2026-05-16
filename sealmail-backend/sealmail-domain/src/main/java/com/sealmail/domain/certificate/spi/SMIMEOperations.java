package com.sealmail.domain.certificate.spi;

import java.util.List;

/**
 * S/MIME cryptographic operations SPI.
 * Domain layer defines the contract; infrastructure layer (BouncyCastle) provides implementation.
 * Uses byte[] as opaque MIME message carrier to keep domain layer free of jakarta.mail dependency.
 */
public interface SMIMEOperations {

    /**
     * 加密邮件（单收件人）
     */
    byte[] encrypt(byte[] mimeMessage, String recipientPemCert);

    /**
     * 加密邮件（多收件人）
     */
    byte[] encryptMultiple(byte[] mimeMessage, List<String> recipientPemCerts);

    /**
     * 使用明确算法族加密邮件（多收件人）。
     */
    default byte[] encryptMultiple(byte[] mimeMessage,
                                   List<String> recipientPemCerts,
                                   SMIMEEncryptionSuite suite) {
        return encryptMultiple(mimeMessage, recipientPemCerts);
    }

    /**
     * 解密邮件
     */
    byte[] decrypt(byte[] encryptedMessage, String privateKeyPem, String certPem);

    /**
     * 签名邮件
     */
    byte[] sign(byte[] mimeMessage, String privateKeyPem, String certPem);

    /**
     * 验证签名并返回详细结果
     */
    SignatureValidationResult verifySignatureDetail(byte[] signedMessage, String senderPemCert);

    /**
     * 简化的签名验证（兼容旧接口）
     */
    default boolean verifySignature(byte[] signedMessage, String senderPemCert) {
        return verifySignatureDetail(signedMessage, senderPemCert).isValid();
    }

    /**
     * 从签名邮件中提取原始内容
     */
    byte[] extractSignedContent(byte[] signedMessage);

    /**
     * 检查邮件是否已加密
     */
    boolean isEncrypted(byte[] message);

    /**
     * 检查邮件是否已签名
     */
    boolean isSigned(byte[] message);
}
