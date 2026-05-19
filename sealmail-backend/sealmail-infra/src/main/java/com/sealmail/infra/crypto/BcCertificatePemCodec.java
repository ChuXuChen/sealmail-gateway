package com.sealmail.infra.crypto;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Base64;

final class BcCertificatePemCodec {

    String certificateToPem(X509Certificate cert) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("-----BEGIN CERTIFICATE-----\n");
            String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                    .encodeToString(cert.getEncoded());
            sb.append(b64);
            if (!b64.endsWith("\n")) {
                sb.append('\n');
            }
            sb.append("-----END CERTIFICATE-----\n");
            return sb.toString();
        } catch (Exception e) {
            throw new BcCertificateCryptoPort.CertificateCryptoException("Failed to encode certificate to PEM", e);
        }
    }

    String privateKeyToPem(PrivateKey privateKey) {
        try (StringWriter sw = new StringWriter(); JcaPEMWriter pw = new JcaPEMWriter(sw)) {
            pw.writeObject(privateKey);
            pw.flush();
            return sw.toString();
        } catch (Exception e) {
            throw new BcCertificateCryptoPort.CertificateCryptoException("Failed to encode private key to PEM", e);
        }
    }

    String crlToPem(X509CRL crl) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("-----BEGIN X509 CRL-----\n");
            String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                    .encodeToString(crl.getEncoded());
            sb.append(b64);
            if (!b64.endsWith("\n")) {
                sb.append('\n');
            }
            sb.append("-----END X509 CRL-----\n");
            return sb.toString();
        } catch (Exception e) {
            throw new BcCertificateCryptoPort.CertificateCryptoException("Failed to encode CRL to PEM", e);
        }
    }

    String csrToPem(PKCS10CertificationRequest csr) {
        try (StringWriter sw = new StringWriter(); JcaPEMWriter writer = new JcaPEMWriter(sw)) {
            writer.writeObject(csr);
            writer.flush();
            return sw.toString();
        } catch (Exception e) {
            throw new BcCertificateCryptoPort.CertificateCryptoException("Failed to encode CSR: " + e.getMessage(), e);
        }
    }

    PrivateKey parsePrivateKey(String pem) throws Exception {
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

    X509Certificate parseCertificate(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj = parser.readObject();
            if (obj instanceof X509CertificateHolder holder) {
                return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
            }
            throw new IllegalArgumentException("PEM data is not an X.509 certificate");
        }
    }

    X509CRL parseCrl(String pem) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509", "BC");
        try (ByteArrayInputStream input = new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8))) {
            return (X509CRL) factory.generateCRL(input);
        }
    }

    X509CRL parseCrl(byte[] der) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509", "BC");
        try (ByteArrayInputStream input = new ByteArrayInputStream(der)) {
            return (X509CRL) factory.generateCRL(input);
        }
    }

    PKCS10CertificationRequest parseCsr(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj = parser.readObject();
            if (obj instanceof PKCS10CertificationRequest csr) {
                return csr;
            }
            throw new IllegalArgumentException("PEM data is not a PKCS#10 CSR");
        }
    }
}
