package com.sealmail.app.usecase.mail;

import com.sealmail.app.dto.response.CryptoKeyMaterialResponse;
import com.sealmail.app.dto.response.SmimeSignatureValidationResponse;
import com.sealmail.domain.certificate.spi.CertificateCryptoPort;
import com.sealmail.domain.certificate.spi.SignatureValidationResult;
import com.sealmail.domain.certificate.spi.SmimeMessageCryptoPort;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.List;

@Service
public class SmimeOperationUseCase {

    private final SmimeMessageCryptoPort smimeMessageCryptoPort;
    private final CertificateCryptoPort certificateCryptoPort;

    public SmimeOperationUseCase(SmimeMessageCryptoPort smimeMessageCryptoPort,
                                 CertificateCryptoPort certificateCryptoPort) {
        this.smimeMessageCryptoPort = smimeMessageCryptoPort;
        this.certificateCryptoPort = certificateCryptoPort;
    }

    public String encrypt(String content, String recipientCert) {
        return smimeMessageCryptoPort.encryptText(content, recipientCert);
    }

    public String encryptMultiple(String content, List<String> recipientCerts) {
        return smimeMessageCryptoPort.encryptTextForRecipients(content, recipientCerts);
    }

    public String decrypt(String encryptedContent, String privateKey, String certificate) {
        return smimeMessageCryptoPort.decryptToText(encryptedContent, privateKey, certificate);
    }

    public String sign(String content, String privateKey, String certificate) {
        return smimeMessageCryptoPort.signText(content, privateKey, certificate);
    }

    public SmimeSignatureValidationResponse verify(String signedContent, String senderCert) {
        SignatureValidationResult result = smimeMessageCryptoPort.verifySignedContent(signedContent, senderCert);
        return new SmimeSignatureValidationResponse(
                result.isValid(),
                result.getSigner(),
                result.getSignerEmail(),
                result.getSigningTime(),
                result.getSignatureAlgorithm(),
                result.getValidationErrors(),
                result.isCertificateTrusted(),
                result.isCertificateRevoked(),
                result.isCertificateExpired(),
                result.isTrusted()
        );
    }

    public String extractSignedContent(String signedContent) {
        return smimeMessageCryptoPort.extractSignedText(signedContent);
    }

    public boolean isEncrypted(String content) {
        return smimeMessageCryptoPort.isEncrypted(content);
    }

    public boolean isSigned(String content) {
        return smimeMessageCryptoPort.isSigned(content);
    }

    public CryptoKeyMaterialResponse generateTestKeyMaterial() {
        CertificateCryptoPort.CertificateMaterial material = issueTestMaterial();
        return new CryptoKeyMaterialResponse(
                material.certificate().algorithm(),
                material.privateKeyPem(),
                material.certificate().pemContent(),
                material.publicKeyFormat(),
                "success"
        );
    }

    public TestKeyMaterial generateTestMaterialIfMissing(String privateKeyPem, String certPem) {
        if (privateKeyPem != null && certPem != null) {
            return new TestKeyMaterial(privateKeyPem, certPem);
        }
        CertificateCryptoPort.CertificateMaterial material = issueTestMaterial();
        return new TestKeyMaterial(material.privateKeyPem(), material.certificate().pemContent());
    }

    public CryptoStatus cryptoStatus() {
        CertificateCryptoPort.CryptoCapabilities capabilities = certificateCryptoPort.cryptoCapabilities();
        return new CryptoStatus(capabilities.supportedAlgorithms(), capabilities.status());
    }

    public SignedPayload signForTest(String content, String privateKeyPem, String certPem) throws Exception {
        TestKeyMaterial material = generateTestMaterialIfMissing(privateKeyPem, certPem);
        String signedBase64 = sign(content, material.privateKeyPem(), material.certificatePem());
        return new SignedPayload(content, signedBase64, "success");
    }

    public EncryptedPayload encryptForTest(String content, String certPem) {
        TestKeyMaterial material = generateTestMaterialIfMissing(null, certPem);
        String encryptedBase64 = encrypt(content, material.certificatePem());
        return new EncryptedPayload(content, encryptedBase64, "success");
    }

    private CertificateCryptoPort.CertificateMaterial issueTestMaterial() {
        return certificateCryptoPort.generateTestMaterial();
    }

    public record TestKeyMaterial(String privateKeyPem, String certificatePem) {
    }

    public record SignedPayload(String originalContent, String signedBase64, String status) {
    }

    public record EncryptedPayload(String originalContent, String encryptedBase64, String status) {
    }

    public record CryptoStatus(Map<String, String> supportedAlgorithms, String status) {
    }
}
