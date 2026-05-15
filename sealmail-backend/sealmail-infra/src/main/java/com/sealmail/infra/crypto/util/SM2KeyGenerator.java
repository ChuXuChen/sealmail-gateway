package com.sealmail.infra.crypto.util;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.gm.GMObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.StringWriter;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * SM2国密密钥对和证书生成工具
 * 用于生成符合GMT 0009-2012标准的SM2密钥和自签名证书
 */
public class SM2KeyGenerator {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * 生成SM2密钥对
     */
    public static KeyPair generateSM2KeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        kpg.initialize(256, new SecureRandom());  // SM2使用256位椭圆曲线
        return kpg.generateKeyPair();
    }

    /**
     * 生成自签名SM2证书
     */
    public static X509Certificate generateSM2Certificate(KeyPair keyPair, String subjectDN) throws Exception {
        long now = System.currentTimeMillis();
        Date startDate = new Date(now);
        Date endDate = new Date(now + 365L * 24 * 60 * 60 * 1000);  // 1年有效期

        BigInteger serialNumber = BigInteger.valueOf(now);
        X500Name issuer = new X500Name(subjectDN);
        X500Name subject = new X500Name(subjectDN);

        // 创建SM3withSM2签名器
        ContentSigner signer = new JcaContentSignerBuilder("SM3withSM2")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.getPrivate());

        // 构建证书
        X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
                issuer,
                serialNumber,
                startDate,
                endDate,
                subject,
                SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded())
        );

        X509CertificateHolder certHolder = certBuilder.build(signer);
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(certHolder);
    }

    /**
     * 将私钥转换为PEM格式字符串
     */
    public static String privateKeyToPEM(PrivateKey privateKey) throws Exception {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(sw)) {
            pemWriter.writeObject(privateKey);
        }
        return sw.toString();
    }

    /**
     * 将证书转换为PEM格式字符串
     */
    public static String certificateToPEM(X509Certificate cert) throws Exception {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(sw)) {
            pemWriter.writeObject(cert);
        }
        return sw.toString();
    }

    /**
     * 生成邮件用户的SM2密钥和证书（用于测试）
     */
    public static void main(String[] args) throws Exception {
        // 发件人证书
        KeyPair senderKey = generateSM2KeyPair();
        X509Certificate senderCert = generateSM2Certificate(senderKey, "CN=cxc1234567892022@163.com,O=SealMail,C=CN");

        System.out.println("=== 发件人SM2证书 ===");
        System.out.println(certificateToPEM(senderCert));

        // 收件人证书
        KeyPair recipientKey = generateSM2KeyPair();
        X509Certificate recipientCert = generateSM2Certificate(recipientKey, "CN=2416507029@qq.com,O=SealMail,C=CN");

        System.out.println("=== 收件人SM2证书（加密用） ===");
        System.out.println(certificateToPEM(recipientCert));
    }
}
