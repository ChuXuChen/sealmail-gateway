package com.sealmail.infra.crypto;

import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.certificate.spi.SMIMEEncryptionSuite;
import com.sealmail.domain.certificate.spi.SignatureValidationResult;
import com.sealmail.domain.mailsecurity.CryptoProfile;
import com.sealmail.infra.config.properties.SmimeCryptoProperties;
import com.sealmail.infra.crypto.util.PemUtils;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.gm.GMObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.smime.SMIMECapabilitiesAttribute;
import org.bouncycastle.asn1.smime.SMIMECapability;
import org.bouncycastle.asn1.smime.SMIMECapabilityVector;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.KeyAgreeRecipientInformation;
import org.bouncycastle.cms.KeyTransRecipientInformation;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.cms.jcajce.JceKeyAgreeEnvelopedRecipient;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.activation.CommandMap;
import jakarta.activation.MailcapCommandMap;
import jakarta.mail.Header;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetHeaders;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.*;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
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
        // 兼容旧存量邮件：旧 GM 实现使用 ECDH + AES wrap 封装内容密钥。
        bc.put("Alg.Alias.SecretKeyFactory.2.16.840.1.101.3.4.1.45", "AES");
        bc.put("Alg.Alias.SecretKeyFactory.2.16.840.1.101.3.4.1.25", "AES");
        bc.put("Alg.Alias.SecretKeyFactory.2.16.840.1.101.3.4.1.5",  "AES");
        // 在 BC Provider 中注册 SM4 CBC OID 的 AlgorithmParameters 别名，
        // 使 CMS/SMIME 解密时 DefaultJcaJceHelper 能通过 BC 解析 SM4 IV 参数
        bc.put("Alg.Alias.AlgorithmParameters.1.2.156.10197.1.104.2", "SM4");
        // 配置 JavaMail 支持 S/MIME
        setupMailcap();
    }

    private final SmimeAlgorithmSuites algorithmSuites;
    private final X509CryptoProfileResolver profileResolver;
    private final SecureRandom secureRandom;

    @Autowired
    public BcSMIMEOperations(SmimeCryptoProperties cryptoProperties) {
        this(new SmimeAlgorithmSuites(cryptoProperties), new X509CryptoProfileResolver(), new SecureRandom());
    }

    BcSMIMEOperations(SmimeAlgorithmSuites algorithmSuites,
                      X509CryptoProfileResolver profileResolver,
                      SecureRandom secureRandom) {
        this.algorithmSuites = algorithmSuites;
        this.profileResolver = profileResolver;
        this.secureRandom = secureRandom;
    }

    BcSMIMEOperations() {
        this(SmimeAlgorithmSuites.defaults(), new X509CryptoProfileResolver(), new SecureRandom());
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
        CryptoProfile profile = inferEncryptionProfile(recipientPemCerts);
        return encryptMultiple(mimeMessage, recipientPemCerts, profile);
    }

    @Override
    public byte[] encryptMultiple(byte[] mimeMessage,
                                  List<String> recipientPemCerts,
                                  SMIMEEncryptionSuite suite) {
        return encryptMultiple(mimeMessage, recipientPemCerts, SmimeAlgorithmSuites.toProfile(suite));
    }

    @Override
    public byte[] encryptMultiple(byte[] mimeMessage,
                                  List<String> recipientPemCerts,
                                  CryptoProfile profile) {
        try {
            SmimeAlgorithmSuite algorithmSuite = algorithmSuites.get(profile);
            RawMimeSections originalMessage = splitMessage(mimeMessage);
            MimeBodyPart msg = extractMimeEntity(mimeMessage);
            SMIMEEnvelopedGenerator gen = new SMIMEEnvelopedGenerator();
            List<X509Certificate> certificates = new ArrayList<>();

            for (String pemCert : recipientPemCerts) {
                X509Certificate cert = PemUtils.parseCertificate(pemCert);
                validateCertificateProfile(cert, profile);
                certificates.add(cert);
            }
            if (certificates.isEmpty()) {
                throw new CryptoException("S/MIME 加密失败: no recipient certificates");
            }

            for (X509Certificate cert : certificates) {
                gen.addRecipientInfoGenerator(createKeyTransRecipientInfoGenerator(cert, algorithmSuite));
            }

            OutputEncryptor encryptor = new ConfigurableContentOutputEncryptor(algorithmSuite, secureRandom);
            log.info("使用 {} profile 加密邮件，收件人: {}, keyAlg={}, contentAlg={}",
                    profile,
                    certificates.size(),
                    algorithmSuite.recipientKeyAlgorithm().getId(),
                    algorithmSuite.contentEncryptionAlgorithm().getId());
            MimeBodyPart encrypted = gen.generate(msg, encryptor);
            return rebuildMessagePreservingOuterHeaders(originalMessage.outerHeaders(), encrypted);
        } catch (Exception e) {
            log.error("S/MIME 加密失败详情: ", e);
            throw new CryptoException("S/MIME 加密失败: " + e.getMessage(), e);
        }
    }

    private JceKeyTransRecipientInfoGenerator createKeyTransRecipientInfoGenerator(
            X509Certificate cert,
            SmimeAlgorithmSuite suite) throws Exception {
        JceKeyTransRecipientInfoGenerator generator = new JceKeyTransRecipientInfoGenerator(
                cert,
                new AlgorithmIdentifier(suite.recipientKeyAlgorithm()));
        generator.setProvider(BouncyCastleProvider.PROVIDER_NAME);
        generator.setAlgorithmMapping(suite.recipientKeyAlgorithm(), suite.recipientKeyCipher());
        return generator;
    }

    private CryptoProfile inferEncryptionProfile(List<String> recipientPemCerts) {
        if (recipientPemCerts == null || recipientPemCerts.isEmpty()) {
            return CryptoProfile.STANDARD;
        }
        try {
            boolean allGm = true;
            boolean allStandard = true;
            for (String recipientPemCert : recipientPemCerts) {
                X509Certificate cert = PemUtils.parseCertificate(recipientPemCert);
                CryptoProfile certProfile = profileResolver.requireProfile(cert);
                allGm &= certProfile == CryptoProfile.GM;
                allStandard &= certProfile == CryptoProfile.STANDARD;
            }
            if (allGm) {
                return CryptoProfile.GM;
            }
            if (allStandard) {
                return CryptoProfile.STANDARD;
            }
            throw new CryptoException("recipient certificates do not share one crypto profile");
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("failed to infer S/MIME crypto profile: " + e.getMessage(), e);
        }
    }

    private void validateCertificateProfile(X509Certificate cert, CryptoProfile profile) {
        if (!profileResolver.matches(cert, profile)) {
            throw new CryptoException(profile + " profile requires compatible recipient certificates");
        }
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
                                keyTransRecipient(privateKey, recipientInfo));
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

    private JceKeyTransEnvelopedRecipient keyTransRecipient(PrivateKey privateKey,
                                                           RecipientInformation recipientInfo) {
        JceKeyTransEnvelopedRecipient recipient = new JceKeyTransEnvelopedRecipient(privateKey);
        recipient.setProvider(BouncyCastleProvider.PROVIDER_NAME);
        recipient.setContentProvider((Provider) null);
        for (CryptoProfile profile : List.of(CryptoProfile.STANDARD, CryptoProfile.GM)) {
            SmimeAlgorithmSuite algorithmSuite = algorithmSuites.get(profile);
            recipient.setAlgorithmMapping(
                    algorithmSuite.recipientKeyAlgorithm(),
                    algorithmSuite.recipientKeyCipher());
        }
        ASN1ObjectIdentifier keyAlg = recipientInfo.getKeyEncryptionAlgorithm().getAlgorithm();
        SmimeAlgorithmSuite matchingSuite = algorithmSuiteForRecipientKeyAlgorithm(keyAlg);
        if (matchingSuite != null) {
            recipient.setAlgorithmMapping(keyAlg, matchingSuite.recipientKeyCipher());
        }
        return recipient;
    }

    private SmimeAlgorithmSuite algorithmSuiteForRecipientKeyAlgorithm(ASN1ObjectIdentifier keyAlgorithm) {
        for (CryptoProfile profile : List.of(CryptoProfile.STANDARD, CryptoProfile.GM)) {
            SmimeAlgorithmSuite algorithmSuite = algorithmSuites.get(profile);
            if (algorithmSuite.recipientKeyAlgorithm().equals(keyAlgorithm)) {
                return algorithmSuite;
            }
        }
        return null;
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
            capabilities.addCapability(algorithmSuites.get(CryptoProfile.GM).contentEncryptionAlgorithm());
            capabilities.addCapability(SM3_OID);            // SM3哈希
            capabilities.addCapability(algorithmSuites.get(CryptoProfile.STANDARD).contentEncryptionAlgorithm());
            capabilities.addCapability(SMIMECapability.aES128_CBC);

            CryptoProfile signingProfile = profileResolver.requireProfile(cert);
            String sigAlg = algorithmSuites.get(signingProfile).signatureAlgorithm();
            log.info("使用签名算法: {}, profile: {}, 密钥类型: {}", sigAlg, signingProfile, privateKey.getAlgorithm());

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
