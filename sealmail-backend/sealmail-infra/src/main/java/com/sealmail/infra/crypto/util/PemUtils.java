package com.sealmail.infra.crypto.util;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEInputDecryptorProviderBuilder;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

public final class PemUtils {

    private PemUtils() {
    }

    public static X509Certificate parseCertificate(String pem) throws CertificateException {
        String certContent = pem
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getMimeDecoder().decode(certContent);
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(decoded));
        } catch (NoSuchProviderException e) {
            // BC 未注册时回退到默认 provider
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(decoded));
        }
    }

    public static PrivateKey parsePrivateKey(String pem) throws Exception {
        return parsePrivateKey(pem, null);
    }

    public static PrivateKey parsePrivateKey(String pem, char[] password) throws Exception {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj = parser.readObject();

            if (obj instanceof PrivateKeyInfo privateKeyInfo) {
                return new JcaPEMKeyConverter().getPrivateKey(privateKeyInfo);
            }

            if (obj instanceof PKCS8EncryptedPrivateKeyInfo encryptedKey) {
                if (password == null) {
                    throw new IllegalArgumentException("Private key is encrypted, password required");
                }
                InputDecryptorProvider decryptorProvider = new JcePKCSPBEInputDecryptorProviderBuilder()
                        .setProvider("BC")
                        .build(password);
                PrivateKeyInfo keyInfo = encryptedKey.decryptPrivateKeyInfo(decryptorProvider);
                return new JcaPEMKeyConverter().getPrivateKey(keyInfo);
            }

            if (obj instanceof KeyPair keyPair) {
                return keyPair.getPrivate();
            }

            if (obj instanceof PEMKeyPair pemKeyPair) {
                return new JcaPEMKeyConverter().getPrivateKey(pemKeyPair.getPrivateKeyInfo());
            }

            throw new IllegalArgumentException("Unsupported private key format: "
                    + (obj != null ? obj.getClass().getName() : "null"));
        }
    }
}
