package com.sealmail.infra.crypto;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.security.*;
import java.security.cert.X509Certificate;
import javax.crypto.Cipher;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S/MIME 加密引擎单元测试
 */
class BcSMIMEOperationsTest {

    private BcSMIMEOperations smimeOperations;
    private static KeyPair keyPair;
    private static X509Certificate certificate;
    private static String certPem;
    private static String privateKeyPem;

    @BeforeAll
    static void setupClass() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        // 生成测试用的 RSA 密钥对
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(2048);
        keyPair = kpg.generateKeyPair();

        // 生成自签名证书
        certificate = generateSelfSignedCertificate(keyPair);
        certPem = convertCertToPem(certificate);
        privateKeyPem = convertPrivateKeyToPem(keyPair.getPrivate());
    }

    @BeforeEach
    void setUp() {
        smimeOperations = new BcSMIMEOperations();
    }

    @Test
    void testSignAndVerify() throws Exception {
        // 准备测试邮件内容
        String testMail = "From: sender@example.com\r\n" +
                "To: recipient@example.com\r\n" +
                "Subject: Test Email\r\n" +
                "\r\n" +
                "This is a test email body.";

        byte[] mimeMessage = testMail.getBytes();

        // 签名
        byte[] signed = smimeOperations.sign(mimeMessage, privateKeyPem, certPem);
        assertNotNull(signed);
        assertTrue(signed.length > mimeMessage.length);

        // 验证签名
        var result = smimeOperations.verifySignatureDetail(signed, certPem);
        assertNotNull(result);
        assertTrue(result.isValid(), "Signature should be valid");
        assertTrue(result.isTrusted(), "Signature should be trusted");
        // 自签名证书可能没有电子邮件，不强制断言
        assertNotNull(result.getSigner());
    }

    @Test
    void testEncryptAndDecrypt() throws Exception {
        // 准备测试邮件内容
        String testMail = "From: sender@example.com\r\n" +
                "To: recipient@example.com\r\n" +
                "Subject: Confidential Email\r\n" +
                "\r\n" +
                "This is confidential content: secret123";

        byte[] mimeMessage = testMail.getBytes();

        // 加密
        byte[] encrypted = smimeOperations.encrypt(mimeMessage, certPem);
        assertNotNull(encrypted);
        assertTrue(encrypted.length > 0);

        // 验证内容已被加密（不包含原文）
        String encryptedStr = new String(encrypted);
        assertFalse(encryptedStr.contains("secret123"), "Encrypted content should not contain plaintext");

        // 解密
        byte[] decrypted = smimeOperations.decrypt(encrypted, privateKeyPem, certPem);
        assertNotNull(decrypted);

        // 验证解密后内容
        String decryptedStr = new String(decrypted);
        assertTrue(decryptedStr.contains("secret123"), "Decrypted content should contain original text");
    }

    @Test
    void testSubjectPreservedAcrossSignEncryptAndDecrypt() throws Exception {
        String testMail = "From: sender@example.com\r\n" +
                "To: recipient@example.com\r\n" +
                "Subject: Preserved Subject\r\n" +
                "MIME-Version: 1.0\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "\r\n" +
                "Subject must remain visible.";

        byte[] signed = smimeOperations.sign(testMail.getBytes(), privateKeyPem, certPem);
        assertEquals("Preserved Subject", parseMimeMessage(signed).getSubject());

        byte[] encrypted = smimeOperations.encrypt(signed, certPem);
        assertEquals("Preserved Subject", parseMimeMessage(encrypted).getSubject());

        byte[] decrypted = smimeOperations.decrypt(encrypted, privateKeyPem, certPem);
        assertEquals("Preserved Subject", parseMimeMessage(decrypted).getSubject());
    }

    @Test
    void testIsEncrypted() throws Exception {
        String plainMail = "From: test@example.com\r\n\r\nPlain text content";
        byte[] encrypted = smimeOperations.encrypt(plainMail.getBytes(), certPem);

        assertTrue(smimeOperations.isEncrypted(encrypted));
        assertFalse(smimeOperations.isEncrypted(plainMail.getBytes()));
    }

    @Test
    void testIsSigned() throws Exception {
        String plainMail = "From: test@example.com\r\n\r\nPlain text content";
        byte[] signed = smimeOperations.sign(plainMail.getBytes(), privateKeyPem, certPem);

        assertTrue(smimeOperations.isSigned(signed));
        assertFalse(smimeOperations.isSigned(plainMail.getBytes()));
    }

    @Test
    void testEncryptMultipleRecipients() throws Exception {
        String testMail = "From: sender@example.com\r\n\r\nMulti-recipient content";
        byte[] mimeMessage = testMail.getBytes();

        // 使用相同证书模拟多个收件人
        java.util.List<String> recipientCerts = java.util.Arrays.asList(certPem, certPem);

        byte[] encrypted = smimeOperations.encryptMultiple(mimeMessage, recipientCerts);
        assertNotNull(encrypted);
        assertTrue(encrypted.length > 0);

        // 解密验证
        byte[] decrypted = smimeOperations.decrypt(encrypted, privateKeyPem, certPem);
        assertTrue(new String(decrypted).contains("Multi-recipient"));
    }

    @Test
    void testExtractSignedContent() throws Exception {
        String testMail = "From: sender@example.com\r\n" +
                "Subject: Signed Subject\r\n" +
                "MIME-Version: 1.0\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "\r\n" +
                "Content to sign";
        byte[] mimeMessage = testMail.getBytes();

        byte[] signed = smimeOperations.sign(mimeMessage, privateKeyPem, certPem);
        byte[] extracted = smimeOperations.extractSignedContent(signed);

        assertNotNull(extracted);
        MimeMessage extractedMessage = parseMimeMessage(extracted);
        assertEquals("Signed Subject", extractedMessage.getSubject());
        assertEquals("Content to sign", extractBodyText(extracted));
    }

    @Test
    void testSM2EncryptAndDecrypt() throws Exception {
        // 确保 SM4OIDProvider 已注册
        smimeOperations = new BcSMIMEOperations();
        Provider sm4oid = Security.getProvider("SM4OID");
        assertNotNull(sm4oid, "SM4OIDProvider should be registered by BcSMIMEOperations");

        // 验证 SM4 OID 可被 Cipher.getInstance 解析
        Cipher sm4Cipher = Cipher.getInstance("1.2.156.10197.1.104.2");
        assertNotNull(sm4Cipher, "SM4 OID cipher should be resolvable");

        // 生成 SM2 密钥对和证书
        KeyPairGenerator sm2Kpg = KeyPairGenerator.getInstance("EC", "BC");
        sm2Kpg.initialize(new java.security.spec.ECGenParameterSpec("sm2p256v1"));
        KeyPair sm2KeyPair = sm2Kpg.generateKeyPair();

        org.bouncycastle.asn1.x500.X500Name sm2Dn =
                new org.bouncycastle.asn1.x500.X500Name("CN=SM2 Test, O=SealMail, E=sm2@test.com");
        long now = System.currentTimeMillis();
        org.bouncycastle.cert.X509v3CertificateBuilder sm2CertBuilder =
                new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                        sm2Dn, java.math.BigInteger.valueOf(now),
                        new Date(now), new Date(now + 365 * 24 * 60 * 60 * 1000L),
                        sm2Dn, sm2KeyPair.getPublic());
        org.bouncycastle.operator.ContentSigner sm2Signer =
                new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SM3withSM2")
                        .setProvider("BC")
                        .build(sm2KeyPair.getPrivate());
        org.bouncycastle.cert.X509CertificateHolder sm2CertHolder = sm2CertBuilder.build(sm2Signer);
        X509Certificate sm2Cert = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(sm2CertHolder);

        String sm2CertPem = convertCertToPem(sm2Cert);
        String sm2PrivPem = convertPrivateKeyToPem(sm2KeyPair.getPrivate());

        String testMail = "From: sender@example.com\r\n" +
                "To: sm2@test.com\r\n" +
                "Subject: SM2+SM4 Test\r\n" +
                "\r\n" +
                "国密全链路测试内容";
        byte[] mimeMessage = testMail.getBytes();

        // 使用 SM2 KeyAgreement + SM4 加密
        byte[] encrypted = smimeOperations.encrypt(mimeMessage, sm2CertPem);
        assertNotNull(encrypted);
        assertTrue(encrypted.length > 0);
        assertTrue(smimeOperations.isEncrypted(encrypted));

        // 解密
        byte[] decrypted = smimeOperations.decrypt(encrypted, sm2PrivPem, sm2CertPem);
        assertNotNull(decrypted);
        String decryptedStr = new String(decrypted);
        assertTrue(decryptedStr.contains("国密全链路测试内容"), "解密内容应包含原文");

        // SM3withSM2 签名 + 验证
        byte[] signed = smimeOperations.sign(mimeMessage, sm2PrivPem, sm2CertPem);
        var result = smimeOperations.verifySignatureDetail(signed, sm2CertPem);
        assertTrue(result.isValid(), "SM2 签名应验证通过");
    }

    // ========== 辅助方法 ==========

    private static X509Certificate generateSelfSignedCertificate(KeyPair keyPair) throws Exception {
        // 使用 Bouncy Castle 生成自签名证书
        org.bouncycastle.asn1.x500.X500Name dn =
                new org.bouncycastle.asn1.x500.X500Name("CN=Test User, O=Test Org, E=sender@example.com");

        long now = System.currentTimeMillis();
        Date startDate = new Date(now);
        Date endDate = new Date(now + 365 * 24 * 60 * 60 * 1000L); // 1 year

        java.math.BigInteger serialNumber = java.math.BigInteger.valueOf(now);

        org.bouncycastle.cert.X509v3CertificateBuilder certBuilder =
                new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                        dn, serialNumber, startDate, endDate, dn, keyPair.getPublic());

        org.bouncycastle.operator.ContentSigner signer =
                new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA")
                        .setProvider("BC")
                        .build(keyPair.getPrivate());

        org.bouncycastle.cert.X509CertificateHolder certHolder = certBuilder.build(signer);
        return new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certHolder);
    }

    private static MimeMessage parseMimeMessage(byte[] data) throws Exception {
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(data));
    }

    private static String extractBodyText(byte[] data) {
        String raw = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        int split = raw.indexOf("\r\n\r\n");
        if (split < 0) {
            split = raw.indexOf("\n\n");
        }
        if (split < 0) {
            return raw.trim();
        }
        String body = raw.substring(split + (raw.charAt(split) == '\r' ? 4 : 2));
        return body.trim();
    }

    private static String convertCertToPem(X509Certificate cert) throws Exception {
        java.io.StringWriter sw = new java.io.StringWriter();
        try (org.bouncycastle.openssl.jcajce.JcaPEMWriter pw =
                     new org.bouncycastle.openssl.jcajce.JcaPEMWriter(sw)) {
            pw.writeObject(cert);
        }
        return sw.toString();
    }

    private static String convertPrivateKeyToPem(PrivateKey privateKey) throws Exception {
        java.io.StringWriter sw = new java.io.StringWriter();
        try (org.bouncycastle.openssl.jcajce.JcaPEMWriter pw =
                     new org.bouncycastle.openssl.jcajce.JcaPEMWriter(sw)) {
            pw.writeObject(privateKey);
        }
        return sw.toString();
    }
}
