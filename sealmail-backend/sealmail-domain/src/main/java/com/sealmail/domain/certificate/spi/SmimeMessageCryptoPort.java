package com.sealmail.domain.certificate.spi;

import java.util.List;

public interface SmimeMessageCryptoPort {

    String encryptText(String content, String recipientCertificate);

    String encryptTextForRecipients(String content, List<String> recipientCertificates);

    String decryptToText(String encryptedContent, String privateKey, String certificate);

    String signText(String content, String privateKey, String certificate);

    SignatureValidationResult verifySignedContent(String signedContent, String senderCertificate);

    String extractSignedText(String signedContent);

    boolean isEncrypted(String content);

    boolean isSigned(String content);
}
