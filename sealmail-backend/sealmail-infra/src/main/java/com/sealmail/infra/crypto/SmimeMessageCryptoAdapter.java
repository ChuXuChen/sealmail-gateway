package com.sealmail.infra.crypto;

import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.certificate.spi.SignatureValidationResult;
import com.sealmail.domain.certificate.spi.SmimeMessageCryptoPort;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Component
public class SmimeMessageCryptoAdapter implements SmimeMessageCryptoPort {

    private final SMIMEOperations smimeOperations;

    public SmimeMessageCryptoAdapter(SMIMEOperations smimeOperations) {
        this.smimeOperations = smimeOperations;
    }

    @Override
    public String encryptText(String content, String recipientCertificate) {
        byte[] encrypted = smimeOperations.encrypt(content.getBytes(StandardCharsets.UTF_8), recipientCertificate);
        return Base64.getMimeEncoder().encodeToString(encrypted);
    }

    @Override
    public String encryptTextForRecipients(String content, List<String> recipientCertificates) {
        byte[] encrypted = smimeOperations.encryptMultiple(content.getBytes(StandardCharsets.UTF_8), recipientCertificates);
        return Base64.getMimeEncoder().encodeToString(encrypted);
    }

    @Override
    public String decryptToText(String encryptedContent, String privateKey, String certificate) {
        byte[] encrypted = Base64.getMimeDecoder().decode(encryptedContent);
        byte[] decrypted = smimeOperations.decrypt(encrypted, privateKey, certificate);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    @Override
    public String signText(String content, String privateKey, String certificate) {
        byte[] signed = smimeOperations.sign(content.getBytes(StandardCharsets.UTF_8), privateKey, certificate);
        return Base64.getMimeEncoder().encodeToString(signed);
    }

    @Override
    public SignatureValidationResult verifySignedContent(String signedContent, String senderCertificate) {
        byte[] signed = Base64.getMimeDecoder().decode(signedContent);
        return smimeOperations.verifySignatureDetail(signed, senderCertificate);
    }

    @Override
    public String extractSignedText(String signedContent) {
        byte[] signed = Base64.getMimeDecoder().decode(signedContent);
        byte[] extracted = smimeOperations.extractSignedContent(signed);
        return new String(extracted, StandardCharsets.UTF_8);
    }

    @Override
    public boolean isEncrypted(String content) {
        return smimeOperations.isEncrypted(Base64.getMimeDecoder().decode(content));
    }

    @Override
    public boolean isSigned(String content) {
        return smimeOperations.isSigned(Base64.getMimeDecoder().decode(content));
    }
}
