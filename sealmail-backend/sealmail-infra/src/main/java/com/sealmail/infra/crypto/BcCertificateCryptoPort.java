package com.sealmail.infra.crypto;

import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.KeyUsage;
import com.sealmail.domain.certificate.ValidityPeriod;
import com.sealmail.domain.certificate.spi.CertificateCryptoPort;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.bouncycastle.x509.extension.X509ExtensionUtil;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class BcCertificateCryptoPort implements CertificateCryptoPort {

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    public CertificateMaterial issueSelfSigned(IssueSelfSignedCommand command) {
        try {
            KeyPair keyPair = generateKeyPair(command.algorithm());
            X509Certificate x509 = issue(CertSpec.builder()
                    .subjectPubKey(keyPair.getPublic())
                    .subjectDn(command.subjectDn())
                    .subjectAlgorithm(command.algorithm())
                    .issuerDn(command.subjectDn())
                    .issuerPrivKey(keyPair.getPrivate())
                    .issuerPubKey(keyPair.getPublic())
                    .validityDays(command.validityDays())
                    .ca(command.ca())
                    .pathLenConstraint(command.pathLenConstraint())
                    .ekus(command.extendedKeyUsages())
                    .crlDpUrl(command.crlDistributionPointUrl())
                    .build());
            return new CertificateMaterial(
                    describe(x509, command.algorithm()),
                    privateKeyToPem(keyPair.getPrivate()),
                    keyPair.getPublic().getFormat());
        } catch (Exception e) {
            throw new CertificateCryptoException("Failed to issue self-signed certificate: " + e.getMessage(), e);
        }
    }

    @Override
    public CertificateMaterial issueWithIssuer(IssueWithIssuerCommand command) {
        try {
            KeyPair subjectKeyPair = generateKeyPair(command.subjectAlgorithm());
            PrivateKey issuerPrivateKey = parsePrivateKey(command.issuerPrivateKeyPem());
            X509Certificate issuer = parseCertificate(command.issuerCertificatePem());
            X509Certificate x509 = issue(CertSpec.builder()
                    .subjectPubKey(subjectKeyPair.getPublic())
                    .subjectDn(command.subjectDn())
                    .subjectAlgorithm(command.subjectAlgorithm())
                    .issuerDn(issuer.getSubjectX500Principal().getName())
                    .issuerPrivKey(issuerPrivateKey)
                    .issuerPubKey(issuer.getPublicKey())
                    .validityDays(command.validityDays())
                    .ca(command.ca())
                    .pathLenConstraint(command.pathLenConstraint())
                    .ekus(command.extendedKeyUsages())
                    .crlDpUrl(command.crlDistributionPointUrl())
                    .build());
            return new CertificateMaterial(
                    describe(x509, command.subjectAlgorithm()),
                    privateKeyToPem(subjectKeyPair.getPrivate()),
                    subjectKeyPair.getPublic().getFormat());
        } catch (Exception e) {
            throw new CertificateCryptoException("Failed to issue certificate: " + e.getMessage(), e);
        }
    }

    @Override
    public CryptoCapabilities cryptoCapabilities() {
        return new CryptoCapabilities(
                Map.of(
                        "signature", "SM3withSM2",
                        "encryption", "SM4-CBC",
                        "hash", "SM3",
                        "keyExchange", "SM2"),
                "ready");
    }

    @Override
    public CertificateDescriptor signCsr(SignCsrCommand command) {
        try {
            PKCS10CertificationRequest csr = parseCsr(command.csrPem());
            validateCsr(csr);
            X509Certificate caCert = parseCertificate(command.issuerCertificatePem());
            PrivateKey caPrivateKey = parsePrivateKey(command.issuerPrivateKeyPem());
            X509Certificate signed = signCsr(
                    csr,
                    caCert,
                    caPrivateKey,
                    command.validityDays(),
                    command.crlDistributionPointUrl());
            return describe(signed, detectAlgorithm(csr.getSubjectPublicKeyInfo()));
        } catch (Exception e) {
            throw new CertificateCryptoException("Failed to sign CSR: " + e.getMessage(), e);
        }
    }

    @Override
    public CsrInfo validateCsr(String csrPem) {
        try {
            PKCS10CertificationRequest csr = parseCsr(csrPem);
            validateCsr(csr);
            return new CsrInfo(csr.getSubject().toString(), extractEmail(csr.getSubject()), detectAlgorithm(csr.getSubjectPublicKeyInfo()));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new CertificateCryptoException("Failed to validate CSR: " + e.getMessage(), e);
        }
    }

    @Override
    public CertificateDescriptor readCertificate(String certificatePem) {
        try {
            X509Certificate x509 = parseCertificate(certificatePem);
            return describe(x509, detectPublicKeyAlgorithm(x509.getPublicKey().getAlgorithm()));
        } catch (Exception e) {
            throw new CertificateCryptoException("Failed to read certificate: " + e.getMessage(), e);
        }
    }

    @Override
    public void validateCertificateMatchesPrivateKey(String certificatePem, String privateKeyPem) {
        try {
            validateCertificateMatchesPrivateKey(parseCertificate(certificatePem), parsePrivateKey(privateKeyPem));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new CertificateCryptoException("Failed to validate private key: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isSelfSigned(String certificatePem) {
        try {
            X509Certificate cert = parseCertificate(certificatePem);
            if (!cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal())) {
                return false;
            }
            cert.verify(cert.getPublicKey(), "BC");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isIssuedBy(String subjectCertificatePem, String issuerCertificatePem) {
        try {
            X509Certificate subject = parseCertificate(subjectCertificatePem);
            X509Certificate issuer = parseCertificate(issuerCertificatePem);
            String subjectAki = extractAuthorityKeyIdentifier(subject);
            String issuerSki = extractSubjectKeyIdentifier(issuer);
            subject.verify(issuer.getPublicKey(), "BC");
            return subjectAki == null || issuerSki == null || subjectAki.equalsIgnoreCase(issuerSki);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public CrlContent normalizeAndValidateCrl(String caCertificatePem, String crlPem, String crlDerBase64) {
        try {
            X509Certificate caX509 = parseCertificate(caCertificatePem);
            X509CRL crl;
            if (crlPem != null && !crlPem.isBlank()) {
                crl = parseCrl(crlPem);
            } else if (crlDerBase64 != null && !crlDerBase64.isBlank()) {
                crl = parseCrl(Base64.getDecoder().decode(crlDerBase64));
            } else {
                throw new IllegalArgumentException("请提供 PEM CRL 或 DER .crl 文件内容");
            }
            if (!crl.getIssuerX500Principal().equals(caX509.getSubjectX500Principal())) {
                throw new IllegalArgumentException("CRL issuer 与所选 CA subject 不一致");
            }
            crl.verify(caX509.getPublicKey(), "BC");
            return new CrlContent(crl.getEncoded(), crlToPem(crl));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new CertificateCryptoException("CRL validation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public CrlContent generateCrl(GenerateCrlCommand command) {
        try {
            X509Certificate caX509 = parseCertificate(command.caCertificatePem());
            PrivateKey caPriv = parsePrivateKey(command.caPrivateKeyPem());
            Date thisUpdate = Date.from(command.thisUpdate());
            Date nextUpdate = Date.from(command.nextUpdate());

            X509v2CRLBuilder builder = new X509v2CRLBuilder(
                    new X500Name(caX509.getSubjectX500Principal().getName()), thisUpdate);
            builder.setNextUpdate(nextUpdate);
            builder.addExtension(Extension.cRLNumber, false,
                    new org.bouncycastle.asn1.x509.CRLNumber(BigInteger.valueOf(command.thisUpdate().getEpochSecond())));

            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                    extUtils.createAuthorityKeyIdentifier(caX509.getPublicKey()));

            for (CrlEntry entry : command.revokedEntries()) {
                if (entry.serialNumber() == null) {
                    continue;
                }
                Date revocationDate = Date.from(entry.revocationDate() != null ? entry.revocationDate() : command.thisUpdate());
                builder.addCRLEntry(entry.serialNumber(), revocationDate, mapReason(entry.reasonCode()));
            }

            String sigAlg = signatureAlgorithmFor(caPriv, "RSA");
            ContentSigner signer = new JcaContentSignerBuilder(sigAlg).setProvider("BC").build(caPriv);
            X509CRLHolder holder = builder.build(signer);
            X509CRL crl = new JcaX509CRLConverter().setProvider("BC").getCRL(holder);
            return new CrlContent(crl.getEncoded(), crlToPem(crl));
        } catch (Exception e) {
            throw new CertificateCryptoException("Failed to generate CRL: " + e.getMessage(), e);
        }
    }

    public String writeCsrPem(PKCS10CertificationRequest csr) {
        try (StringWriter sw = new StringWriter(); JcaPEMWriter writer = new JcaPEMWriter(sw)) {
            writer.writeObject(csr);
            writer.flush();
            return sw.toString();
        } catch (Exception e) {
            throw new CertificateCryptoException("Failed to encode CSR: " + e.getMessage(), e);
        }
    }

    KeyPair generateKeyPair(String algorithm) throws Exception {
        KeyPairGenerator keyGen;
        if ("SM2".equals(algorithm)) {
            keyGen = KeyPairGenerator.getInstance("EC", "BC");
            keyGen.initialize(new ECGenParameterSpec("sm2p256v1"), new SecureRandom());
        } else {
            keyGen = KeyPairGenerator.getInstance("RSA", "BC");
            keyGen.initialize(2048, new SecureRandom());
        }
        return keyGen.generateKeyPair();
    }

    X509Certificate issue(CertSpec spec) throws Exception {
        X500Name subject = new X500Name(spec.subjectDn);
        X500Name issuer = new X500Name(spec.issuerDn);
        BigInteger serial = new BigInteger(64, new SecureRandom());
        Date notBefore = new Date();
        Date notAfter = new Date(notBefore.getTime() + (long) spec.validityDays * 86_400_000L);

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, subject, spec.subjectPubKey);
        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                extUtils.createSubjectKeyIdentifier(spec.subjectPubKey));
        PublicKey akiSource = spec.issuerPubKey != null ? spec.issuerPubKey : spec.subjectPubKey;
        builder.addExtension(Extension.authorityKeyIdentifier, false,
                extUtils.createAuthorityKeyIdentifier(akiSource));

        if (spec.ca) {
            builder.addExtension(Extension.basicConstraints, true,
                    new BasicConstraints(Math.max(spec.pathLenConstraint, 0)));
        } else {
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        }

        int kuBits;
        if (spec.ca) {
            kuBits = org.bouncycastle.asn1.x509.KeyUsage.keyCertSign
                    | org.bouncycastle.asn1.x509.KeyUsage.cRLSign;
        } else if ("SM2".equals(spec.subjectAlgorithm)) {
            kuBits = org.bouncycastle.asn1.x509.KeyUsage.digitalSignature
                    | org.bouncycastle.asn1.x509.KeyUsage.keyAgreement;
        } else {
            kuBits = org.bouncycastle.asn1.x509.KeyUsage.digitalSignature
                    | org.bouncycastle.asn1.x509.KeyUsage.keyEncipherment;
        }
        builder.addExtension(Extension.keyUsage, true, new org.bouncycastle.asn1.x509.KeyUsage(kuBits));

        if (spec.ekus != null && !spec.ekus.isEmpty()) {
            KeyPurposeId[] purposes = spec.ekus.stream()
                    .map(oid -> KeyPurposeId.getInstance(new ASN1ObjectIdentifier(oid)))
                    .toArray(KeyPurposeId[]::new);
            builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(purposes));
        }

        if (spec.crlDpUrl != null && !spec.crlDpUrl.isBlank()) {
            DistributionPointName dpn = new DistributionPointName(
                    new GeneralNames(new GeneralName(
                            GeneralName.uniformResourceIdentifier, spec.crlDpUrl)));
            builder.addExtension(Extension.cRLDistributionPoints, false,
                    new CRLDistPoint(new DistributionPoint[]{new DistributionPoint(dpn, null, null)}));
        }

        ContentSigner signer = new JcaContentSignerBuilder(signatureAlgorithmFor(spec.issuerPrivKey, spec.subjectAlgorithm))
                .setProvider("BC")
                .build(spec.issuerPrivKey);
        return new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(builder.build(signer));
    }

    private X509Certificate signCsr(PKCS10CertificationRequest csr,
                                    X509Certificate caCert,
                                    PrivateKey caPrivateKey,
                                    int validityDays,
                                    String crlDpUrl) throws Exception {
        JcaPKCS10CertificationRequest jcaCsr = new JcaPKCS10CertificationRequest(csr).setProvider("BC");
        PublicKey subjectPubKey = jcaCsr.getPublicKey();
        String subjectAlgo = "EC".equalsIgnoreCase(subjectPubKey.getAlgorithm())
                || "ECDSA".equalsIgnoreCase(subjectPubKey.getAlgorithm()) ? "SM2" : "RSA";
        return issue(CertSpec.builder()
                .subjectPubKey(subjectPubKey)
                .subjectDn(csr.getSubject().toString())
                .subjectAlgorithm(subjectAlgo)
                .issuerDn(caCert.getSubjectX500Principal().getName())
                .issuerPrivKey(caPrivateKey)
                .issuerPubKey(caCert.getPublicKey())
                .validityDays(validityDays)
                .ca(false)
                .ekus(Set.of(EKU_EMAIL_PROTECTION))
                .crlDpUrl(crlDpUrl)
                .build());
    }

    private void validateCsr(PKCS10CertificationRequest csr) throws Exception {
        if (csr == null) {
            throw new IllegalArgumentException("CSR cannot be null");
        }
        if (!csr.isSignatureValid(new JcaContentVerifierProviderBuilder()
                .setProvider("BC")
                .build(csr.getSubjectPublicKeyInfo()))) {
            throw new IllegalArgumentException("CSR signature is invalid");
        }
    }

    private CertificateDescriptor describe(X509Certificate x509, String algorithm) throws Exception {
        Integer pathLen = null;
        int basicConstraints = x509.getBasicConstraints();
        if (basicConstraints >= 0) {
            pathLen = basicConstraints == Integer.MAX_VALUE ? 0 : basicConstraints;
        }
        return new CertificateDescriptor(
                certificateToPem(x509),
                algorithm,
                computeThumbprint(x509),
                new ValidityPeriod(
                        Instant.ofEpochMilli(x509.getNotBefore().getTime()),
                        Instant.ofEpochMilli(x509.getNotAfter().getTime())),
                detectDomainKeyUsages(x509),
                x509.getIssuerX500Principal().getName(),
                x509.getSubjectX500Principal().getName(),
                x509.getSerialNumber(),
                extractSubjectKeyIdentifier(x509),
                pathLen != null,
                pathLen,
                readExtendedKeyUsages(x509),
                extractCrlDistributionPointUrl(x509));
    }

    private Set<String> readExtendedKeyUsages(X509Certificate x509) {
        try {
            List<String> eku = x509.getExtendedKeyUsage();
            return eku == null ? Set.of() : new LinkedHashSet<>(eku);
        } catch (Exception e) {
            return Set.of();
        }
    }

    private Set<KeyUsage> detectDomainKeyUsages(X509Certificate x509) {
        boolean[] ku = x509.getKeyUsage();
        if (ku == null) {
            return EnumSet.of(KeyUsage.SIGNING, KeyUsage.ENCRYPTION);
        }
        Set<KeyUsage> out = EnumSet.noneOf(KeyUsage.class);
        if (ku.length > 0 && ku[0]) out.add(KeyUsage.SIGNING);
        if (ku.length > 1 && ku[1]) out.add(KeyUsage.SIGNING);
        if (ku.length > 2 && ku[2]) out.add(KeyUsage.ENCRYPTION);
        if (ku.length > 3 && ku[3]) out.add(KeyUsage.ENCRYPTION);
        if (ku.length > 4 && ku[4]) out.add(KeyUsage.ENCRYPTION);
        if (ku.length > 5 && ku[5]) out.add(KeyUsage.SIGNING);
        if (ku.length > 6 && ku[6]) out.add(KeyUsage.SIGNING);
        if (out.isEmpty()) out.add(KeyUsage.SIGNING);
        return out;
    }

    private String signatureAlgorithmFor(PrivateKey issuerKey, String fallbackSubjectAlgo) {
        String issuerAlg = issuerKey.getAlgorithm();
        if ("EC".equalsIgnoreCase(issuerAlg) || "ECDSA".equalsIgnoreCase(issuerAlg)) {
            return "SM3withSM2";
        }
        if ("RSA".equalsIgnoreCase(issuerAlg)) {
            return "SHA256withRSA";
        }
        return "SM2".equals(fallbackSubjectAlgo) ? "SM3withSM2" : "SHA256withRSA";
    }

    private String computeThumbprint(X509Certificate cert) throws Exception {
        String digest = switch (detectPublicKeyAlgorithm(cert.getPublicKey().getAlgorithm())) {
            case "SM2" -> "SM3";
            default -> "SHA-256";
        };
        MessageDigest md = MessageDigest.getInstance(digest, "BC");
        byte[] fp = md.digest(cert.getEncoded());
        StringBuilder sb = new StringBuilder();
        for (byte b : fp) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String extractSubjectKeyIdentifier(X509Certificate cert) {
        try {
            byte[] skiValue = cert.getExtensionValue(Extension.subjectKeyIdentifier.getId());
            if (skiValue == null) return null;
            SubjectKeyIdentifier skid = SubjectKeyIdentifier.getInstance(
                    X509ExtensionUtil.fromExtensionValue(skiValue));
            return toHex(skid.getKeyIdentifier());
        } catch (Exception e) {
            return null;
        }
    }

    private String extractAuthorityKeyIdentifier(X509Certificate cert) {
        try {
            byte[] akiValue = cert.getExtensionValue(Extension.authorityKeyIdentifier.getId());
            if (akiValue == null) return null;
            AuthorityKeyIdentifier akid = AuthorityKeyIdentifier.getInstance(
                    X509ExtensionUtil.fromExtensionValue(akiValue));
            return akid.getKeyIdentifier() == null ? null : toHex(akid.getKeyIdentifier());
        } catch (Exception e) {
            return null;
        }
    }

    private String extractCrlDistributionPointUrl(X509Certificate cert) {
        try {
            byte[] ext = cert.getExtensionValue(Extension.cRLDistributionPoints.getId());
            if (ext == null) {
                return null;
            }
            CRLDistPoint distPoint = CRLDistPoint.getInstance(X509ExtensionUtil.fromExtensionValue(ext));
            if (distPoint == null) {
                return null;
            }
            for (DistributionPoint dp : distPoint.getDistributionPoints()) {
                DistributionPointName name = dp.getDistributionPoint();
                if (name == null || name.getType() != DistributionPointName.FULL_NAME) {
                    continue;
                }
                GeneralNames generalNames = GeneralNames.getInstance(name.getName());
                for (GeneralName generalName : generalNames.getNames()) {
                    if (generalName.getTagNo() == GeneralName.uniformResourceIdentifier) {
                        return generalName.getName().toString();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String extractEmail(X500Name subject) {
        for (RDN rdn : subject.getRDNs(BCStyle.EmailAddress)) {
            String email = readRdn(rdn);
            if (email != null) return email;
        }
        for (RDN rdn : subject.getRDNs(BCStyle.E)) {
            String email = readRdn(rdn);
            if (email != null) return email;
        }
        for (RDN rdn : subject.getRDNs(BCStyle.CN)) {
            String cn = readRdn(rdn);
            if (cn != null && cn.contains("@")) return cn;
        }
        return null;
    }

    private String readRdn(RDN rdn) {
        ASN1Encodable enc = rdn.getFirst() == null ? null : rdn.getFirst().getValue();
        return enc == null ? null : IETFUtils.valueToString(enc);
    }

    private String detectAlgorithm(SubjectPublicKeyInfo spki) {
        String oid = spki.getAlgorithm().getAlgorithm().getId();
        if ("1.2.840.113549.1.1.1".equals(oid)) return "RSA";
        if ("1.2.840.10045.2.1".equals(oid) || oid.startsWith("1.2.156.10197.1.301")) return "SM2";
        return oid;
    }

    private String detectPublicKeyAlgorithm(String javaAlg) {
        if (javaAlg == null) return "UNKNOWN";
        return switch (javaAlg.toUpperCase()) {
            case "EC", "ECDSA", "SM2" -> "SM2";
            case "RSA" -> "RSA";
            default -> javaAlg;
        };
    }

    private String certificateToPem(X509Certificate cert) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("-----BEGIN CERTIFICATE-----\n");
            String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(cert.getEncoded());
            sb.append(b64);
            if (!b64.endsWith("\n")) sb.append('\n');
            sb.append("-----END CERTIFICATE-----\n");
            return sb.toString();
        } catch (Exception e) {
            throw new CertificateCryptoException("Failed to encode certificate to PEM", e);
        }
    }

    private String privateKeyToPem(PrivateKey privateKey) {
        try (StringWriter sw = new StringWriter(); JcaPEMWriter pw = new JcaPEMWriter(sw)) {
            pw.writeObject(privateKey);
            pw.flush();
            return sw.toString();
        } catch (Exception e) {
            throw new CertificateCryptoException("Failed to encode private key to PEM", e);
        }
    }

    private String crlToPem(X509CRL crl) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("-----BEGIN X509 CRL-----\n");
            String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(crl.getEncoded());
            sb.append(b64);
            if (!b64.endsWith("\n")) sb.append('\n');
            sb.append("-----END X509 CRL-----\n");
            return sb.toString();
        } catch (Exception e) {
            throw new CertificateCryptoException("Failed to encode CRL to PEM", e);
        }
    }

    private PrivateKey parsePrivateKey(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            Object obj;
            while ((obj = parser.readObject()) != null) {
                if (obj instanceof PrivateKeyInfo pki) {
                    return converter.getPrivateKey(pki);
                }
                if (obj instanceof PEMKeyPair pkp) {
                    return converter.getKeyPair(pkp).getPrivate();
                }
                if (obj instanceof KeyPair kp) {
                    return kp.getPrivate();
                }
            }
        }
        throw new IllegalArgumentException("No private key found in PEM data");
    }

    private X509Certificate parseCertificate(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj = parser.readObject();
            if (obj instanceof X509CertificateHolder holder) {
                return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
            }
            throw new IllegalArgumentException("PEM data is not an X.509 certificate");
        }
    }

    private X509CRL parseCrl(String pem) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509", "BC");
        try (ByteArrayInputStream input = new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8))) {
            return (X509CRL) factory.generateCRL(input);
        }
    }

    private X509CRL parseCrl(byte[] der) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509", "BC");
        try (ByteArrayInputStream input = new ByteArrayInputStream(der)) {
            return (X509CRL) factory.generateCRL(input);
        }
    }

    private PKCS10CertificationRequest parseCsr(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj = parser.readObject();
            if (obj instanceof PKCS10CertificationRequest csr) {
                return csr;
            }
            throw new IllegalArgumentException("PEM data is not a PKCS#10 CSR");
        }
    }

    private void validateCertificateMatchesPrivateKey(X509Certificate certificate, PrivateKey privateKey) throws Exception {
        PublicKey expected = certificate.getPublicKey();
        PublicKey actual = derivePublicKey(privateKey);
        if (actual == null) {
            throw new IllegalArgumentException("Unsupported private key type: " + privateKey.getAlgorithm());
        }
        if (!MessageDigest.isEqual(expected.getEncoded(), actual.getEncoded())) {
            throw new IllegalArgumentException("Private key does not match certificate public key");
        }
    }

    private PublicKey derivePublicKey(PrivateKey priv) throws Exception {
        if (priv instanceof RSAPrivateCrtKey rsa) {
            RSAPublicKeySpec spec = new RSAPublicKeySpec(rsa.getModulus(), rsa.getPublicExponent());
            return KeyFactory.getInstance("RSA", "BC").generatePublic(spec);
        }

        PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(priv.getEncoded());
        if (privateKeyInfo != null
                && privateKeyInfo.getPrivateKeyAlgorithm() != null
                && "1.2.840.10045.2.1".equals(privateKeyInfo.getPrivateKeyAlgorithm().getAlgorithm().getId())) {
            org.bouncycastle.crypto.params.AsymmetricKeyParameter privateParams =
                    org.bouncycastle.crypto.util.PrivateKeyFactory.createKey(priv.getEncoded());
            if (privateParams instanceof org.bouncycastle.crypto.params.ECPrivateKeyParameters ecPrivate) {
                org.bouncycastle.crypto.params.ECDomainParameters domain = ecPrivate.getParameters();
                org.bouncycastle.math.ec.ECPoint q = domain.getG().multiply(ecPrivate.getD()).normalize();
                org.bouncycastle.crypto.params.ECPublicKeyParameters publicParams =
                        new org.bouncycastle.crypto.params.ECPublicKeyParameters(q, domain);
                SubjectPublicKeyInfo spki = org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory
                        .createSubjectPublicKeyInfo(publicParams);
                return KeyFactory.getInstance("EC", "BC")
                        .generatePublic(new X509EncodedKeySpec(spki.getEncoded()));
            }
        }
        return null;
    }

    private int mapReason(String reasonCode) {
        if (reasonCode == null) return CRLReason.unspecified;
        return switch (reasonCode.toUpperCase()) {
            case "KEY_COMPROMISE" -> CRLReason.keyCompromise;
            case "CA_COMPROMISE" -> CRLReason.cACompromise;
            case "AFFILIATION_CHANGED" -> CRLReason.affiliationChanged;
            case "SUPERSEDED" -> CRLReason.superseded;
            case "CESSATION_OF_OPERATION" -> CRLReason.cessationOfOperation;
            case "CERTIFICATE_HOLD" -> CRLReason.certificateHold;
            case "PRIVILEGE_WITHDRAWN" -> CRLReason.privilegeWithdrawn;
            case "AA_COMPROMISE" -> CRLReason.aACompromise;
            default -> CRLReason.unspecified;
        };
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @lombok.Value
    @lombok.Builder
    static class CertSpec {
        PublicKey subjectPubKey;
        String subjectDn;
        String subjectAlgorithm;
        String issuerDn;
        PrivateKey issuerPrivKey;
        PublicKey issuerPubKey;
        int validityDays;
        boolean ca;
        int pathLenConstraint;
        Set<String> ekus;
        String crlDpUrl;
    }

    public static class CertificateCryptoException extends RuntimeException {
        public CertificateCryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
