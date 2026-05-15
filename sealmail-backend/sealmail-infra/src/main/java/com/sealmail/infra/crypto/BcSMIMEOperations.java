package com.sealmail.infra.crypto;

import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.certificate.spi.SignatureValidationResult;
import com.sealmail.infra.crypto.util.PemUtils;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.gm.GMObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.smime.SMIMECapabilitiesAttribute;
import org.bouncycastle.asn1.smime.SMIMECapability;
import org.bouncycastle.asn1.smime.SMIMECapabilityVector;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.KeyAgreeRecipientInformation;
import org.bouncycastle.cms.KeyTransRecipientInformation;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyAgreeEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyAgreeRecipientInfoGenerator;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.mail.smime.SMIMEEnveloped;
import org.bouncycastle.mail.smime.SMIMEEnvelopedGenerator;
import org.bouncycastle.mail.smime.SMIMESigned;
import org.bouncycastle.mail.smime.SMIMESignedGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.mail.smime.util.SharedFileInputStream;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.operator.jcajce.JceGenericKey;
import org.springframework.stereotype.Component;

import jakarta.activation.CommandMap;
import jakarta.activation.MailcapCommandMap;
import jakarta.mail.Header;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetHeaders;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.*;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 完整的 Bouncy Castle S/MIME 加密引擎实现
 * 支持国密算法（SM2/SM3/SM4）和国际标准算法（RSA/AES/SHA256）
 */
@Component
public class BcSMIMEOperations implements SMIMEOperations {

    private static final Logger log = LoggerFactory.getLogger(BcSMIMEOperations.class);

    // 国密算法 OID
    public static final ASN1ObjectIdentifier SM2_OID = GMObjectIdentifiers.sm2p256v1;
    public static final ASN1ObjectIdentifier SM3_OID = GMObjectIdentifiers.sm3;
    public static final ASN1ObjectIdentifier SM4_CBC_OID = GMObjectIdentifiers.sms4_cbc;

    // 算法配置
    private static final String SM2_SIG_ALG = "SM3withSM2";        // 国密签名算法
    private static final String RSA_SIG_ALG = "SHA256withRSA";    // RSA签名算法
    private static final ASN1ObjectIdentifier SM4_ENC_ALG = SM4_CBC_OID;  // 国密加密算法
    private static final ASN1ObjectIdentifier AES_ENC_ALG = CMSAlgorithm.AES256_CBC;  // AES加密算法

    static {
        // 注册 SM4 OID Provider（必须在 BC 之前，用于 CMS/SMIME 解密时解析 SM4 OID）
        if (Security.getProvider("SM4OID") == null) {
            Security.insertProviderAt(new SM4OIDProvider(), 1);
        }
        // 注册 Bouncy Castle 提供者
        Provider bc = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (bc == null) {
            bc = new BouncyCastleProvider();
            Security.addProvider(bc);
        }
        // 为 AES KeyWrap OID 注册 SecretKeyFactory 别名，使 SM2 KeyAgreement 解密时可派生 AES wrap key
        bc.put("Alg.Alias.SecretKeyFactory.2.16.840.1.101.3.4.1.45", "AES");
        bc.put("Alg.Alias.SecretKeyFactory.2.16.840.1.101.3.4.1.25", "AES");
        bc.put("Alg.Alias.SecretKeyFactory.2.16.840.1.101.3.4.1.5",  "AES");
        // 在 BC Provider 中注册 SM4 CBC OID 的 AlgorithmParameters 别名，
        // 使 CMS/SMIME 解密时 DefaultJcaJceHelper 能通过 BC 解析 SM4 IV 参数
        bc.put("Alg.Alias.AlgorithmParameters.1.2.156.10197.1.104.2", "SM4");
        // 配置 JavaMail 支持 S/MIME
        setupMailcap();
    }

    private static void setupMailcap() {
        MailcapCommandMap mc = (MailcapCommandMap) CommandMap.getDefaultCommandMap();
        mc.addMailcap("application/pkcs7-signature;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.pkcs7_signature");
        mc.addMailcap("application/pkcs7-mime;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.pkcs7_mime");
        mc.addMailcap("application/x-pkcs7-signature;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.x_pkcs7_signature");
        mc.addMailcap("application/x-pkcs7-mime;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.x_pkcs7_mime");
        mc.addMailcap("multipart/signed;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.multipart_signed");
        CommandMap.setDefaultCommandMap(mc);
    }

    @Override
    public byte[] encrypt(byte[] mimeMessage, String recipientPemCert) {
        return encryptMultiple(mimeMessage, List.of(recipientPemCert));
    }

    @Override
    public byte[] encryptMultiple(byte[] mimeMessage, List<String> recipientPemCerts) {
        try {
            RawMimeSections originalMessage = splitMessage(mimeMessage);
            MimeBodyPart msg = extractMimeEntity(mimeMessage);
            SMIMEEnvelopedGenerator gen = new SMIMEEnvelopedGenerator();

            List<X509Certificate> rsaCerts = new ArrayList<>();
            List<X509Certificate> sm2Certs = new ArrayList<>();

            for (String pemCert : recipientPemCerts) {
                X509Certificate cert = PemUtils.parseCertificate(pemCert);
                String keyAlg = cert.getPublicKey().getAlgorithm();
                if ("EC".equals(keyAlg) || "SM2".equals(keyAlg)) {
                    sm2Certs.add(cert);
                } else {
                    rsaCerts.add(cert);
                }
            }

            boolean useSM4 = !sm2Certs.isEmpty();

            // 添加 RSA KeyTrans recipients
            for (X509Certificate cert : rsaCerts) {
                gen.addRecipientInfoGenerator(new JceKeyTransRecipientInfoGenerator(cert)
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME));
            }

            // 添加 SM2 KeyAgree recipients
            if (!sm2Certs.isEmpty()) {
                // 使用第一个收件人证书的EC参数生成临时密钥对，确保domain parameters一致
                java.security.interfaces.ECPublicKey firstRecipientPub =
                        (java.security.interfaces.ECPublicKey) sm2Certs.get(0).getPublicKey();
                java.security.spec.ECParameterSpec ecSpec = firstRecipientPub.getParams();

                KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
                kpg.initialize(ecSpec);
                KeyPair ephemeral = kpg.generateKeyPair();

                // 使用 AES-256-WRAP 作为 key wrap 算法（BC 的 KeyAgree KDF 不支持 SM4 OID）
                JceKeyAgreeRecipientInfoGenerator keyAgreeGen =
                        new JceKeyAgreeRecipientInfoGenerator(
                                CMSAlgorithm.ECDH_SHA1KDF,
                                ephemeral.getPrivate(),
                                ephemeral.getPublic(),
                                CMSAlgorithm.AES256_WRAP
                        ).setProvider(BouncyCastleProvider.PROVIDER_NAME);

                for (X509Certificate cert : sm2Certs) {
                    keyAgreeGen.addRecipient(cert);
                }
                gen.addRecipientInfoGenerator(keyAgreeGen);
            }

            // 选择内容加密算法
            OutputEncryptor encryptor;
            if (useSM4) {
                encryptor = createSM4OutputEncryptor();
                log.info("使用 SM2 KeyAgreement + SM4 加密邮件，RSA收件人: {}, SM2收件人: {}",
                        rsaCerts.size(), sm2Certs.size());
            } else {
                encryptor = new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES256_CBC)
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                        .build();
                log.info("使用 RSA KeyTransport + AES-256 加密邮件，收件人: {}", rsaCerts.size());
            }

            MimeBodyPart encrypted = gen.generate(msg, encryptor);
            return rebuildMessagePreservingOuterHeaders(originalMessage.outerHeaders(), encrypted);
        } catch (Exception e) {
            log.error("S/MIME 加密失败详情: ", e);
            throw new CryptoException("S/MIME 加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建 SM4/CBC/PKCS7Padding OutputEncryptor，绕过 BC 的 JceCMSContentEncryptorBuilder
     * 对 SM4 OID 解析的缺陷。
     */
    private OutputEncryptor createSM4OutputEncryptor() throws Exception {
        SecureRandom random = new SecureRandom();

        byte[] keyBytes = new byte[16]; // SM4 128-bit key
        random.nextBytes(keyBytes);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "SM4");

        byte[] iv = new byte[16]; // SM4 CBC 128-bit IV
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS7Padding", BouncyCastleProvider.PROVIDER_NAME);
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));

        AlgorithmIdentifier algId = new AlgorithmIdentifier(SM4_CBC_OID, new DEROctetString(iv));

        return new OutputEncryptor() {
            @Override
            public org.bouncycastle.asn1.x509.AlgorithmIdentifier getAlgorithmIdentifier() {
                return algId;
            }

            @Override
            public OutputStream getOutputStream(OutputStream out) {
                return new CipherOutputStream(out, cipher);
            }

            @Override
            public org.bouncycastle.operator.GenericKey getKey() {
                return new JceGenericKey(algId, key);
            }
        };
    }

    @Override
    public byte[] decrypt(byte[] encryptedMessage, String privateKeyPem, String certPem) {
        try {
            PrivateKey privateKey = PemUtils.parsePrivateKey(privateKeyPem);
            RawMimeSections originalMessage = splitMessage(encryptedMessage);
            MimeBodyPart encryptedPart = extractMimeEntity(encryptedMessage);

            SMIMEEnveloped enveloped = new SMIMEEnveloped(encryptedPart);
            var recipientInfos = enveloped.getRecipientInfos();

            // 尝试匹配每个接收者信息
            for (var recipientInfo : recipientInfos.getRecipients()) {
                try {
                    byte[] decryptedBytes;
                    if (recipientInfo instanceof KeyTransRecipientInformation) {
                        decryptedBytes = (byte[]) recipientInfo.getContent(
                                new JceKeyTransEnvelopedRecipient(privateKey)
                                        .setProvider(BouncyCastleProvider.PROVIDER_NAME));
                    } else if (recipientInfo instanceof KeyAgreeRecipientInformation) {
                        // KeyAgreement 用 BC，content cipher 用全局 provider 查找（以便 SM4OIDProvider 生效）
                        decryptedBytes = (byte[]) recipientInfo.getContent(
                                new JceKeyAgreeEnvelopedRecipient(privateKey)
                                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                        .setContentProvider((Provider) null));
                    } else {
                        continue;
                    }
                    return rebuildMessagePreservingOuterHeaders(
                            originalMessage.outerHeaders(),
                            parseMimeBodyPart(decryptedBytes)
                    );
                } catch (Exception e) {
                    log.warn("尝试解密失败 ({}): {}", recipientInfo.getClass().getSimpleName(), e.getMessage(), e);
                }
            }
            throw new CryptoException("无法使用提供的私钥解密邮件（可能不匹配任何收件人）");
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("S/MIME 解密失败", e);
        }
    }

    @Override
    public byte[] sign(byte[] mimeMessage, String privateKeyPem, String certPem) {
        try {
            PrivateKey privateKey = PemUtils.parsePrivateKey(privateKeyPem);
            X509Certificate cert = PemUtils.parseCertificate(certPem);
            RawMimeSections originalMessage = splitMessage(mimeMessage);
            MimeBodyPart msg = extractMimeEntity(mimeMessage);

            SMIMESignedGenerator gen = new SMIMESignedGenerator();

            // 添加签名能力声明 - 国密算法优先
            SMIMECapabilityVector capabilities = new SMIMECapabilityVector();
            capabilities.addCapability(SM4_CBC_OID);      // SM4加密
            capabilities.addCapability(SM3_OID);            // SM3哈希
            capabilities.addCapability(SMIMECapability.aES256_CBC);
            capabilities.addCapability(SMIMECapability.aES128_CBC);

            // 智能选择算法：根据密钥类型自动选择
            String keyAlg = privateKey.getAlgorithm();
            String sigAlg = ("EC".equals(keyAlg) || "ECDSA".equals(keyAlg)) ? SM2_SIG_ALG : RSA_SIG_ALG;
            log.info("使用签名算法: {}, 密钥类型: {}", sigAlg, keyAlg);

            gen.addSignerInfoGenerator(
                    new JcaSimpleSignerInfoGeneratorBuilder()
                            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                            .build(sigAlg, privateKey, cert));

            // 添加证书链
            List<X509Certificate> certs = new ArrayList<>();
            certs.add(cert);
            JcaCertStore certStore = new JcaCertStore(certs);
            gen.addCertificates(certStore);

            MimeMultipart signed = gen.generate(msg);
            MimeBodyPart signedWrapper = new MimeBodyPart();
            signedWrapper.setContent(signed);
            signedWrapper.setHeader("Content-Type", signed.getContentType());
            return rebuildMessagePreservingOuterHeaders(originalMessage.outerHeaders(), signedWrapper);
        } catch (Exception e) {
            throw new CryptoException("S/MIME 签名失败", e);
        }
    }

    @Override
    public SignatureValidationResult verifySignatureDetail(byte[] signedMessage, String senderPemCert) {
        try {
            X509Certificate senderCert = PemUtils.parseCertificate(senderPemCert);
            MimeBodyPart signedPart = extractMimeEntity(signedMessage);
            Object content = signedPart.getContent();
            SMIMESigned signed;
            if (content instanceof MimeMultipart multipart) {
                signed = new SMIMESigned(multipart);
            } else if (content instanceof byte[] bytes) {
                CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(new byte[0]), bytes);
                return verifyCMSData(cms, senderCert);
            } else {
                signed = new SMIMESigned(signedPart);
            }

            return verifySMIMESigned(signed, senderCert);
        } catch (Exception e) {
            return SignatureValidationResult.builder()
                    .valid(false)
                    .validationErrors(List.of("签名验证异常: " + e.getMessage()))
                    .build();
        }
    }

    private SignatureValidationResult verifySMIMESigned(SMIMESigned signed, X509Certificate senderCert)
            throws Exception {
        var verifierBuilder = new JcaSimpleSignerInfoVerifierBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME);

        var signerInfos = signed.getSignerInfos();

        // 检查证书状态
        boolean expired = false;
        boolean notYetValid = false;
        try {
            senderCert.checkValidity();
        } catch (CertificateExpiredException e) {
            expired = true;
        } catch (CertificateNotYetValidException e) {
            notYetValid = true;
        }

        for (SignerInformation signer : signerInfos.getSigners()) {
            try {
                boolean valid = signer.verify(verifierBuilder.build(senderCert));

                if (valid) {
                    String signerEmail = extractEmailFromCert(senderCert);
                    Instant signingTime = extractSigningTime(signer);

                    return SignatureValidationResult.builder()
                            .valid(true)
                            .signer(senderCert.getSubjectX500Principal().getName())
                            .signerEmail(signerEmail)
                            .signingTime(signingTime)
                            .signatureAlgorithm(signer.getEncryptionAlgOID())
                            .certificateExpired(expired)
                            .certificateRevoked(false) // 后续集成 CRL/OCSP
                            .certificateTrusted(true)  // 后续集成信任链验证
                            .validationErrors(List.of())
                            .build();
                }
            } catch (OperatorCreationException e) {
                // 继续尝试
            }
        }

        return SignatureValidationResult.builder()
                .valid(false)
                .validationErrors(List.of("签名验证失败：未找到匹配的签名者或签名被篡改"))
                .certificateExpired(expired)
                .build();
    }

    private SignatureValidationResult verifyCMSData(CMSSignedData cms, X509Certificate senderCert)
            throws Exception {
        var verifierBuilder = new JcaSimpleSignerInfoVerifierBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME);

        var signerInfos = cms.getSignerInfos();
        for (SignerInformation signer : signerInfos.getSigners()) {
            try {
                boolean valid = signer.verify(verifierBuilder.build(senderCert));
                if (valid) {
                    String signerEmail = extractEmailFromCert(senderCert);
                    return SignatureValidationResult.builder()
                            .valid(true)
                            .signer(senderCert.getSubjectX500Principal().getName())
                            .signerEmail(signerEmail)
                            .signingTime(extractSigningTime(signer))
                            .signatureAlgorithm(signer.getEncryptionAlgOID())
                            .certificateTrusted(true)
                            .build();
                }
            } catch (Exception ignored) {
            }
        }
        return SignatureValidationResult.builder()
                .valid(false)
                .validationErrors(List.of("CMS 签名验证失败"))
                .build();
    }

    @Override
    public byte[] extractSignedContent(byte[] signedMessage) {
        try {
            RawMimeSections originalMessage = splitMessage(signedMessage);
            MimeBodyPart signedPart = extractMimeEntity(signedMessage);
            Object content = signedPart.getContent();
            SMIMESigned signed;
            if (content instanceof MimeMultipart multipart) {
                signed = new SMIMESigned(multipart);
            } else {
                signed = new SMIMESigned(signedPart);
            }
            MimeBodyPart contentPart = signed.getContent();
            return rebuildMessagePreservingOuterHeaders(originalMessage.outerHeaders(), contentPart);
        } catch (Exception e) {
            throw new CryptoException("提取签名邮件内容失败", e);
        }
    }

    @Override
    public boolean isEncrypted(byte[] message) {
        try {
            MimeBodyPart part = extractMimeEntity(message);
            String contentType = part.getContentType();
            return contentType != null && (
                    contentType.contains("application/pkcs7-mime") ||
                    contentType.contains("application/x-pkcs7-mime") ||
                    contentType.contains("name=\"smime.p7m\"")
            );
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isSigned(byte[] message) {
        try {
            MimeBodyPart part = extractMimeEntity(message);
            String contentType = part.getContentType();
            return contentType != null && (
                    contentType.contains("multipart/signed") ||
                    contentType.contains("application/pkcs7-signature") ||
                    contentType.contains("application/x-pkcs7-signature")
            );
        } catch (Exception e) {
            return false;
        }
    }

    // ============ 内部辅助方法 ============

    private String extractEmailFromCert(X509Certificate cert) {
        // 从证书主题中提取邮箱
        String subject = cert.getSubjectX500Principal().getName();
        for (String part : subject.split(",")) {
            if (part.trim().startsWith("EMAILADDRESS=")) {
                return part.trim().substring(14);
            }
        }
        // 检查 Subject Alternative Name
        try {
            var sans = cert.getSubjectAlternativeNames();
            if (sans != null) {
                for (var san : sans) {
                    if ((Integer) san.get(0) == 1) { // rfc822Name
                        return (String) san.get(1);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Instant extractSigningTime(SignerInformation signer) {
        try {
            var signedAttrs = signer.getSignedAttributes();
            if (signedAttrs != null) {
                var attr = signedAttrs.get(PKCSObjectIdentifiers.pkcs_9_at_signingTime);
                if (attr != null && attr.getAttrValues().size() > 0) {
                    Object timeObj = attr.getAttrValues().getObjectAt(0);
                    if (timeObj instanceof Date date) {
                        return date.toInstant();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private MimeBodyPart parseMimeBodyPart(byte[] data) throws MessagingException, IOException {
        try (InputStream is = new ByteArrayInputStream(data)) {
            // 尝试直接解析
            return new MimeBodyPart(is);
        } catch (Exception e) {
            // 如果解析失败，创建一个简单的 body part
            InternetHeaders headers = new InternetHeaders();
            headers.addHeader("Content-Type", "application/octet-stream");
            return new MimeBodyPart(headers, data);
        }
    }

    private MimeBodyPart extractMimeEntity(byte[] message) throws Exception {
        RawMimeSections sections = splitMessage(message);
        if (sections.contentHeaders().length == 0) {
            InternetHeaders headers = new InternetHeaders();
            headers.addHeader("Content-Type", "text/plain; charset=us-ascii");
            return new MimeBodyPart(headers, sections.body());
        }

        ByteArrayOutputStream entity = new ByteArrayOutputStream();
        entity.write(sections.contentHeaders());
        entity.write("\r\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        entity.write(sections.body());
        return parseMimeBodyPart(entity.toByteArray());
    }

    private byte[] rebuildMessagePreservingOuterHeaders(byte[] outerHeaders, MimeBodyPart contentPart) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(outerHeaders);
        out.write(serializeMimePart(contentPart));
        return out.toByteArray();
    }

    private int findBodyStart(byte[] rawMessage) {
        for (int i = 0; i < rawMessage.length - 3; i++) {
            if (rawMessage[i] == '\r' && rawMessage[i + 1] == '\n'
                    && rawMessage[i + 2] == '\r' && rawMessage[i + 3] == '\n') {
                return i + 4;
            }
        }
        for (int i = 0; i < rawMessage.length - 1; i++) {
            if (rawMessage[i] == '\n' && rawMessage[i + 1] == '\n') {
                return i + 2;
            }
        }
        return rawMessage.length;
    }

    private RawMimeSections splitMessage(byte[] rawMessage) {
        int bodyStart = findBodyStart(rawMessage);
        int headerEnd = Math.max(0, bodyStart - (bodyStart >= 4
                && rawMessage[bodyStart - 4] == '\r'
                && rawMessage[bodyStart - 3] == '\n'
                && rawMessage[bodyStart - 2] == '\r'
                && rawMessage[bodyStart - 1] == '\n' ? 4 : 2));

        byte[] headerBytes = Arrays.copyOfRange(rawMessage, 0, Math.max(0, headerEnd));
        byte[] bodyBytes = Arrays.copyOfRange(rawMessage, Math.min(bodyStart, rawMessage.length), rawMessage.length);

        List<String> unfoldedHeaders = unfoldHeaders(new String(headerBytes, java.nio.charset.StandardCharsets.ISO_8859_1));
        ByteArrayOutputStream outer = new ByteArrayOutputStream();
        ByteArrayOutputStream content = new ByteArrayOutputStream();

        for (String headerLine : unfoldedHeaders) {
            int colon = headerLine.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = headerLine.substring(0, colon).trim();
            String value = headerLine.substring(colon + 1).trim();
            if (isContentHeader(name)) {
                writeHeaderLine(content, name, value);
            } else if (shouldPreserveOuterHeader(name)) {
                writeHeaderLine(outer, name, value);
            }
        }

        return new RawMimeSections(outer.toByteArray(), content.toByteArray(), bodyBytes);
    }

    private List<String> unfoldHeaders(String rawHeaders) {
        String[] physicalLines = rawHeaders.split("\\r?\\n");
        List<String> logicalLines = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : physicalLines) {
            if (line.isEmpty()) {
                continue;
            }
            if ((line.startsWith(" ") || line.startsWith("\t")) && current.length() > 0) {
                current.append(' ').append(line.trim());
                continue;
            }
            if (current.length() > 0) {
                logicalLines.add(current.toString());
            }
            current.setLength(0);
            current.append(line);
        }

        if (current.length() > 0) {
            logicalLines.add(current.toString());
        }

        return logicalLines;
    }

    private void writeHeaderLine(OutputStream out, String name, String value) {
        try {
            out.write((name + ": " + value + "\r\n").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            throw new CryptoException("写入MIME头失败", e);
        }
    }

    private boolean shouldPreserveOuterHeader(String name) {
        return !isContentHeader(name) && !isHiddenRecipientHeader(name);
    }

    private boolean isContentHeader(String name) {
        return name != null && (
                name.regionMatches(true, 0, "Content-", 0, "Content-".length())
                        || "MIME-Version".equalsIgnoreCase(name)
        );
    }

    private boolean isHiddenRecipientHeader(String name) {
        return "Bcc".equalsIgnoreCase(name) || "Resent-Bcc".equalsIgnoreCase(name);
    }

    private byte[] serializeMimePart(MimeBodyPart part) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        part.writeTo(out);
        byte[] raw = out.toByteArray();
        int start = 0;
        while (start < raw.length && (raw[start] == '\r' || raw[start] == '\n')) {
            start++;
        }
        return start == 0 ? raw : Arrays.copyOfRange(raw, start, raw.length);
    }

    private byte[] serializeMultipart(MimeMultipart multipart) throws Exception {
        // 将 MimeMultipart 包装在 MimeBodyPart 以确保正确的 Content-Type 头包含边界信息
        MimeBodyPart wrapper = new MimeBodyPart();
        wrapper.setContent(multipart);
        // 确保 Content-Type 被正确设置
        wrapper.setHeader("Content-Type", multipart.getContentType());
        return serializeMimePart(wrapper);
    }

    /**
     * 加密异常
     */
    public static class CryptoException extends RuntimeException {
        public CryptoException(String message) {
            super(message);
        }

        public CryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record RawMimeSections(byte[] outerHeaders, byte[] contentHeaders, byte[] body) {
    }
}
