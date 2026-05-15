package com.sealmail.app.usecase.certificate;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.KeyUsage;
import com.sealmail.domain.certificate.ValidityPeriod;
import com.sealmail.domain.shared.model.EmailAddress;
import lombok.Builder;
import lombok.Value;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.bouncycastle.x509.extension.X509ExtensionUtil;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.cert.X509CRL;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Central place for X.509 issuance and parsing. Every certificate this service
 * produces is PKIX-shaped: SKI/AKI/BasicConstraints/KeyUsage/EKU/CRLDistributionPoints
 * are populated according to the {@link CertSpec} profile.
 */
@Component
public class CertificateCryptoService {

    /** RFC 5280 id-kp-emailProtection OID */
    public static final String EKU_EMAIL_PROTECTION = "1.3.6.1.5.5.7.3.4";

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public KeyPair generateKeyPair(String algorithm) throws Exception {
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

    /**
     * Issue an X.509 certificate per the given spec. Pass {@code issuerPubKey == subjectPubKey}
     * (or leave it null) to produce a self-signed cert.
     */
    public X509Certificate issue(CertSpec spec) throws Exception {
        X500Name subject = new X500Name(spec.subjectDn);
        X500Name issuer = new X500Name(spec.issuerDn);
        BigInteger serial = new BigInteger(64, new SecureRandom());
        Date notBefore = new Date();
        Date notAfter = new Date(notBefore.getTime() + (long) spec.validityDays * 86_400_000L);

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, subject, spec.subjectPubKey);

        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();

        // Subject Key Identifier (always present)
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                extUtils.createSubjectKeyIdentifier(spec.subjectPubKey));

        // Authority Key Identifier (point at issuer; self-signed AKI = SKI of self)
        PublicKey akiSource = spec.issuerPubKey != null ? spec.issuerPubKey : spec.subjectPubKey;
        builder.addExtension(Extension.authorityKeyIdentifier, false,
                extUtils.createAuthorityKeyIdentifier(akiSource));

        // BasicConstraints (critical)
        if (spec.ca) {
            builder.addExtension(Extension.basicConstraints, true,
                    new BasicConstraints(Math.max(spec.pathLenConstraint, 0)));
        } else {
            builder.addExtension(Extension.basicConstraints, true,
                    new BasicConstraints(false));
        }

        // KeyUsage (critical): CA = keyCertSign|cRLSign;
        // end-entity SM2 = digitalSignature|keyAgreement; end-entity RSA = digitalSignature|keyEncipherment.
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
        builder.addExtension(Extension.keyUsage, true,
                new org.bouncycastle.asn1.x509.KeyUsage(kuBits));

        // Extended Key Usage (end-entities typically: emailProtection)
        if (spec.ekus != null && !spec.ekus.isEmpty()) {
            KeyPurposeId[] purposes = spec.ekus.stream()
                    .map(oid -> KeyPurposeId.getInstance(new ASN1ObjectIdentifier(oid)))
                    .toArray(KeyPurposeId[]::new);
            builder.addExtension(Extension.extendedKeyUsage, false,
                    new ExtendedKeyUsage(purposes));
        }

        // CRL Distribution Point (only meaningful for certs issued under a CA)
        if (spec.crlDpUrl != null && !spec.crlDpUrl.isBlank()) {
            DistributionPointName dpn = new DistributionPointName(
                    new GeneralNames(new GeneralName(
                            GeneralName.uniformResourceIdentifier, spec.crlDpUrl)));
            DistributionPoint dp = new DistributionPoint(dpn, null, null);
            builder.addExtension(Extension.cRLDistributionPoints, false,
                    new CRLDistPoint(new DistributionPoint[]{dp}));
        }

        String sigAlg = signatureAlgorithmFor(spec.issuerPrivKey, spec.subjectAlgorithm);
        ContentSigner signer = new JcaContentSignerBuilder(sigAlg)
                .setProvider("BC")
                .build(spec.issuerPrivKey);

        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
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

    public Certificate toDomainCertificate(CertificateId id, EmailAddress owner,
                                            X509Certificate x509, String algorithm) {
        return toDomainCertificate(id, owner, x509, algorithm, false);
    }

    public Certificate toIssuedDomainCertificate(CertificateId id, EmailAddress owner,
                                                 X509Certificate x509, String algorithm) {
        return toDomainCertificate(id, owner, x509, algorithm, true);
    }

    private Certificate toDomainCertificate(CertificateId id, EmailAddress owner,
                                            X509Certificate x509, String algorithm,
                                            boolean issued) {
        ValidityPeriod validity = new ValidityPeriod(
                Instant.ofEpochMilli(x509.getNotBefore().getTime()),
                Instant.ofEpochMilli(x509.getNotAfter().getTime()));

        Certificate cert = issued
                ? Certificate.issueCertificate(
                        id,
                        owner,
                        toPem(x509),
                        validity,
                        detectDomainKeyUsages(x509),
                        x509.getIssuerX500Principal().getName(),
                        x509.getSubjectX500Principal().getName(),
                        x509.getSerialNumber(),
                        extractSubjectKeyIdentifier(x509))
                : Certificate.importCertificate(
                        id,
                        owner,
                        toPem(x509),
                        validity,
                        detectDomainKeyUsages(x509),
                        x509.getIssuerX500Principal().getName(),
                        x509.getSubjectX500Principal().getName(),
                        x509.getSerialNumber(),
                        extractSubjectKeyIdentifier(x509));
        cert.setAlgorithm(algorithm);

        // Mirror PKIX BasicConstraints/EKU back onto the aggregate so the rest of
        // the system doesn't have to re-parse the PEM.
        int bc = x509.getBasicConstraints();
        if (bc != -1) {
            cert.markAsCA(bc == Integer.MAX_VALUE ? 0 : bc);
        }
        try {
            java.util.List<String> eku = x509.getExtendedKeyUsage();
            if (eku != null && !eku.isEmpty()) {
                cert.setExtendedKeyUsages(new LinkedHashSet<>(eku));
            }
        } catch (Exception ignored) {
        }
        cert.setCrlDistributionPointUrl(extractCrlDistributionPointUrl(x509));
        return cert;
    }

    private Set<KeyUsage> detectDomainKeyUsages(X509Certificate x509) {
        boolean[] ku = x509.getKeyUsage();
        if (ku == null) {
            return EnumSet.of(KeyUsage.SIGNING, KeyUsage.ENCRYPTION);
        }
        Set<KeyUsage> out = EnumSet.noneOf(KeyUsage.class);
        if (ku.length > 0 && ku[0]) out.add(KeyUsage.SIGNING);              // digitalSignature
        if (ku.length > 1 && ku[1]) out.add(KeyUsage.SIGNING);              // nonRepudiation
        if (ku.length > 2 && ku[2]) out.add(KeyUsage.ENCRYPTION);           // keyEncipherment
        if (ku.length > 3 && ku[3]) out.add(KeyUsage.ENCRYPTION);           // dataEncipherment
        if (ku.length > 4 && ku[4]) out.add(KeyUsage.ENCRYPTION);           // keyAgreement
        // CA bits (keyCertSign / cRLSign) — we keep SIGNING as a coarse marker
        if (ku.length > 5 && ku[5]) out.add(KeyUsage.SIGNING);
        if (ku.length > 6 && ku[6]) out.add(KeyUsage.SIGNING);
        if (out.isEmpty()) out.add(KeyUsage.SIGNING);
        return out;
    }

    public String computeThumbprint(X509Certificate cert) throws Exception {
        return computeThumbprint(cert.getEncoded(), cert.getPublicKey().getAlgorithm());
    }

    public String computeThumbprint(byte[] derBytes, String publicKeyAlgorithm) throws Exception {
        String digest = digestNameFor(publicKeyAlgorithm);
        MessageDigest md = MessageDigest.getInstance(digest, "BC");
        byte[] fp = md.digest(derBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : fp) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String digestNameFor(String publicKeyAlgorithm) {
        if (publicKeyAlgorithm == null) return "SHA-256";
        return switch (publicKeyAlgorithm.toUpperCase()) {
            case "EC", "ECDSA", "SM2" -> "SM3";
            default -> "SHA-256";
        };
    }

    public String extractSubjectKeyIdentifier(X509Certificate cert) {
        try {
            byte[] skiValue = cert.getExtensionValue(Extension.subjectKeyIdentifier.getId());
            if (skiValue == null) return null;
            SubjectKeyIdentifier skid = SubjectKeyIdentifier.getInstance(
                    X509ExtensionUtil.fromExtensionValue(skiValue));
            byte[] keyId = skid.getKeyIdentifier();
            StringBuilder sb = new StringBuilder();
            for (byte b : keyId) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public String extractAuthorityKeyIdentifier(X509Certificate cert) {
        try {
            byte[] akiValue = cert.getExtensionValue(Extension.authorityKeyIdentifier.getId());
            if (akiValue == null) return null;
            AuthorityKeyIdentifier akid = AuthorityKeyIdentifier.getInstance(
                    X509ExtensionUtil.fromExtensionValue(akiValue));
            byte[] keyId = akid.getKeyIdentifier();
            if (keyId == null || keyId.length == 0) return null;
            StringBuilder sb = new StringBuilder();
            for (byte b : keyId) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isSelfSigned(X509Certificate cert) {
        try {
            if (!cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal())) {
                return false;
            }
            cert.verify(cert.getPublicKey(), "BC");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isIssuedBy(X509Certificate subject, X509Certificate issuer) {
        try {
            String subjectAki = extractAuthorityKeyIdentifier(subject);
            String issuerSki = extractSubjectKeyIdentifier(issuer);
            subject.verify(issuer.getPublicKey(), "BC");
            if (subjectAki != null && issuerSki != null && !subjectAki.equalsIgnoreCase(issuerSki)) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String extractCrlDistributionPointUrl(X509Certificate cert) {
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

    public String toPem(X509Certificate cert) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("-----BEGIN CERTIFICATE-----\n");
            String b64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(cert.getEncoded());
            sb.append(b64);
            if (!b64.endsWith("\n")) sb.append('\n');
            sb.append("-----END CERTIFICATE-----\n");
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode certificate to PEM", e);
        }
    }

    public String privateKeyToPem(PrivateKey privateKey) {
        try (StringWriter sw = new StringWriter();
             JcaPEMWriter pw = new JcaPEMWriter(sw)) {
            pw.writeObject(privateKey);
            pw.flush();
            return sw.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode private key to PEM", e);
        }
    }

    public PrivateKey parsePrivateKey(String pem) throws Exception {
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

    public X509Certificate parseCertificate(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj = parser.readObject();
            if (obj instanceof X509CertificateHolder holder) {
                return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
            }
            throw new IllegalArgumentException("PEM data is not an X.509 certificate");
        }
    }

    public X509CRL parseCrl(String pem) throws Exception {
        java.security.cert.CertificateFactory factory =
                java.security.cert.CertificateFactory.getInstance("X.509", "BC");
        try (java.io.ByteArrayInputStream input =
                     new java.io.ByteArrayInputStream(pem.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return (X509CRL) factory.generateCRL(input);
        }
    }

    public X509CRL parseCrl(byte[] der) throws Exception {
        java.security.cert.CertificateFactory factory =
                java.security.cert.CertificateFactory.getInstance("X.509", "BC");
        try (java.io.ByteArrayInputStream input = new java.io.ByteArrayInputStream(der)) {
            return (X509CRL) factory.generateCRL(input);
        }
    }

    public String toPem(X509CRL crl) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("-----BEGIN X509 CRL-----\n");
            String b64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(crl.getEncoded());
            sb.append(b64);
            if (!b64.endsWith("\n")) sb.append('\n');
            sb.append("-----END X509 CRL-----\n");
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode CRL to PEM", e);
        }
    }

    public PKCS10CertificationRequest parseCsr(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj = parser.readObject();
            if (obj instanceof PKCS10CertificationRequest csr) {
                return csr;
            }
            throw new IllegalArgumentException("PEM data is not a PKCS#10 CSR");
        }
    }

    public void validateCsr(PKCS10CertificationRequest csr) throws Exception {
        if (csr == null) {
            throw new IllegalArgumentException("CSR cannot be null");
        }
        if (!csr.isSignatureValid(new JcaContentVerifierProviderBuilder()
                .setProvider("BC")
                .build(csr.getSubjectPublicKeyInfo()))) {
            throw new IllegalArgumentException("CSR signature is invalid");
        }
    }

    public void validateCertificateMatchesPrivateKey(X509Certificate certificate, PrivateKey privateKey) throws Exception {
        if (certificate == null) {
            throw new IllegalArgumentException("Certificate cannot be null");
        }
        if (privateKey == null) {
            throw new IllegalArgumentException("Private key cannot be null");
        }

        PublicKey expected = certificate.getPublicKey();
        PublicKey actual = derivePublicKey(privateKey);
        if (actual == null) {
            throw new IllegalArgumentException("Unsupported private key type: " + privateKey.getAlgorithm());
        }
        if (!MessageDigest.isEqual(expected.getEncoded(), actual.getEncoded())) {
            throw new IllegalArgumentException("Private key does not match certificate public key");
        }
    }

    /**
     * Sign a CSR with the given CA. The signature algorithm is chosen from the CA's
     * private key. The signed cert is always an end-entity (CA=false) with emailProtection EKU.
     */
    public X509Certificate signCsr(PKCS10CertificationRequest csr,
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

    public PublicKey derivePublicKeyIfPossible(PrivateKey priv) {
        try {
            return derivePublicKey(priv);
        } catch (Exception ignored) {
        }
        return null;
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

    public String detectAlgorithm(SubjectPublicKeyInfo spki) {
        String oid = spki.getAlgorithm().getAlgorithm().getId();
        if ("1.2.840.113549.1.1.1".equals(oid)) return "RSA";
        if ("1.2.840.10045.2.1".equals(oid) || oid.startsWith("1.2.156.10197.1.301")) return "SM2";
        return oid;
    }

    /**
     * Issuance profile carried into {@link #issue(CertSpec)}. Use the builder.
     */
    @Value
    @Builder
    public static class CertSpec {
        PublicKey subjectPubKey;
        String subjectDn;
        /** "RSA" or "SM2" — drives subject-side keyUsage selection. */
        String subjectAlgorithm;

        String issuerDn;
        PrivateKey issuerPrivKey;
        /** Issuer public key for AKI; null for self-signed (AKI will mirror SKI). */
        PublicKey issuerPubKey;

        int validityDays;

        boolean ca;
        /** Path length constraint for CAs; 0 means cannot sign further CAs. Ignored if ca=false. */
        int pathLenConstraint;

        /** OID strings for EKU (e.g. {@link #EKU_EMAIL_PROTECTION}). */
        Set<String> ekus;

        /** If non-blank, embed as CRL Distribution Point extension. */
        String crlDpUrl;
    }
}
