package com.sealmail.infra.crypto;

import com.sealmail.domain.shared.model.EmailAddress;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.security.*;
import java.security.cert.X509Certificate;

@Service
public class KeyStoreService {

    private static final Logger log = LoggerFactory.getLogger(KeyStoreService.class);

    private final KeyStore keyStore;
    private final String keyStorePath;
    private final char[] keyStorePassword;

    public KeyStoreService(
            @Value("${sealmail.security.keystore.path:sealmail-keystore.p12}") String keyStorePath,
            @Value("${sealmail.security.keystore.password:changeit}") String keyStorePassword) throws Exception {
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword.toCharArray();
        this.keyStore = KeyStore.getInstance("PKCS12");

        try {
            keyStore.load(new FileInputStream(keyStorePath), this.keyStorePassword);
            log.info("Loaded existing keystore from {}", keyStorePath);
        } catch (Exception e) {
            log.warn("Keystore not found, creating new one: {}", keyStorePath);
            keyStore.load(null, this.keyStorePassword);
            saveKeyStore();
        }
    }

    public PrivateKey getPrivateKey(EmailAddress emailAddress) {
        try {
            String alias = emailAddress.getValue();
            Key key = keyStore.getKey(alias, keyStorePassword);
            if (key instanceof PrivateKey) {
                return (PrivateKey) key;
            }
            log.warn("No private key found for {}", emailAddress);
            return null;
        } catch (Exception e) {
            log.error("Failed to retrieve private key for {}: {}", emailAddress, e.getMessage());
            return null;
        }
    }

    public String getPrivateKeyPem(EmailAddress emailAddress) {
        PrivateKey privateKey = getPrivateKey(emailAddress);
        if (privateKey == null) {
            return null;
        }
        try {
            StringWriter writer = new StringWriter();
            JcaPEMWriter pemWriter = new JcaPEMWriter(writer);
            pemWriter.writeObject(privateKey);
            pemWriter.close();
            return writer.toString();
        } catch (Exception e) {
            log.error("Failed to export private key for {}: {}", emailAddress, e.getMessage());
            return null;
        }
    }

    public void storeKeyPair(EmailAddress emailAddress, PrivateKey privateKey, X509Certificate certificate) {
        try {
            String alias = emailAddress.getValue();
            keyStore.setKeyEntry(alias, privateKey, keyStorePassword,
                    new java.security.cert.Certificate[]{certificate});
            saveKeyStore();
            log.info("Stored key pair for {}", emailAddress);
        } catch (Exception e) {
            log.error("Failed to store key pair for {}: {}", emailAddress, e.getMessage());
            throw new RuntimeException("Failed to store key pair", e);
        }
    }

    public boolean hasKey(EmailAddress emailAddress) {
        try {
            return keyStore.containsAlias(emailAddress.getValue());
        } catch (Exception e) {
            return false;
        }
    }

    public KeyPair generateKeyPair(EmailAddress emailAddress) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
            keyGen.initialize(2048, new SecureRandom());
            KeyPair keyPair = keyGen.generateKeyPair();
            log.info("Generated key pair for {}", emailAddress);
            return keyPair;
        } catch (Exception e) {
            log.error("Failed to generate key pair for {}: {}", emailAddress, e.getMessage());
            throw new RuntimeException("Failed to generate key pair", e);
        }
    }

    private void saveKeyStore() {
        try (FileOutputStream fos = new FileOutputStream(keyStorePath)) {
            keyStore.store(fos, keyStorePassword);
        } catch (Exception e) {
            log.error("Failed to save keystore: {}", e.getMessage());
            throw new RuntimeException("Failed to save keystore", e);
        }
    }
}
