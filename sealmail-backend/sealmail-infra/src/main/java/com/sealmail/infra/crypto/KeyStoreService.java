package com.sealmail.infra.crypto;

import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.crypto.util.PemUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.security.*;
import java.security.cert.X509Certificate;

@Service
public class KeyStoreService {

    private static final Logger log = LoggerFactory.getLogger(KeyStoreService.class);
    private static final String KEYSTORE_TYPE = "PKCS12";

    private final KeyStore keyStore;
    private final Path keyStorePath;
    private final char[] keyStorePassword;

    public KeyStoreService(
            @Value("${sealmail.security.keystore.path:${SEALMAIL_KEYSTORE_PATH:}}") String keyStorePath,
            @Value("${sealmail.security.keystore.password:${SEALMAIL_KEYSTORE_PASSWORD:}}") String keyStorePassword) throws Exception {
        String configuredPath = keyStorePath == null ? "" : keyStorePath.trim();
        String configuredPassword = keyStorePassword == null ? "" : keyStorePassword;
        this.keyStorePath = configuredPath.isBlank() ? null : Path.of(configuredPath).toAbsolutePath().normalize();
        if (this.keyStorePath != null && configuredPassword.isBlank()) {
            throw new IllegalStateException("SEALMAIL_KEYSTORE_PASSWORD must be set when SEALMAIL_KEYSTORE_PATH is configured");
        }
        this.keyStorePassword = configuredPassword.toCharArray();
        ensureBouncyCastleProvider();
        this.keyStore = KeyStore.getInstance(KEYSTORE_TYPE, BouncyCastleProvider.PROVIDER_NAME);

        if (this.keyStorePath == null) {
            log.warn("Keystore path is not configured; in-memory keystore will be used for this process");
            keyStore.load(null, this.keyStorePassword);
            return;
        }

        Path parent = this.keyStorePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.exists(this.keyStorePath)) {
            if (!Files.isRegularFile(this.keyStorePath)) {
                throw new IllegalStateException("Configured keystore path is not a regular file: " + this.keyStorePath);
            }
            try (InputStream in = Files.newInputStream(this.keyStorePath, StandardOpenOption.READ)) {
                keyStore.load(in, this.keyStorePassword);
                log.info("Loaded existing keystore from {}", this.keyStorePath);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to load configured keystore " + this.keyStorePath
                                + "; check SEALMAIL_KEYSTORE_PASSWORD and file integrity",
                        e);
            }
        } else {
            keyStore.load(null, this.keyStorePassword);
            saveKeyStore();
            log.info("Created new keystore at {}", this.keyStorePath);
        }
    }

    public PrivateKey getPrivateKey(EmailAddress emailAddress) {
        return getPrivateKeyByAlias(emailAddress.getValue());
    }

    public synchronized PrivateKey getPrivateKeyByAlias(String alias) {
        try {
            Key key = keyStore.getKey(alias, keyStorePassword);
            if (key instanceof PrivateKey) {
                return (PrivateKey) key;
            }
            log.warn("No private key found for alias {}", alias);
            return null;
        } catch (Exception e) {
            log.error("Failed to retrieve private key for alias {}: {}", alias, e.getMessage());
            return null;
        }
    }

    public String getPrivateKeyPem(EmailAddress emailAddress) {
        return getPrivateKeyPemByAlias(emailAddress.getValue());
    }

    public String getPrivateKeyPemByAlias(String alias) {
        PrivateKey privateKey = getPrivateKeyByAlias(alias);
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
            log.error("Failed to export private key for alias {}: {}", alias, e.getMessage());
            return null;
        }
    }

    public void storeKeyPair(EmailAddress emailAddress, PrivateKey privateKey, X509Certificate certificate) {
        storeKeyPair(emailAddress.getValue(), privateKey, certificate);
    }

    public synchronized void storeKeyPair(String alias, PrivateKey privateKey, X509Certificate certificate) {
        try {
            keyStore.setKeyEntry(alias, privateKey, keyStorePassword,
                    new java.security.cert.Certificate[]{certificate});
            saveKeyStore();
            log.info("Stored key pair for alias {}", alias);
        } catch (Exception e) {
            log.error("Failed to store key pair for alias {}: {}", alias, e.getMessage());
            throw new RuntimeException("Failed to store key pair", e);
        }
    }

    public void storePemKeyPair(String alias, String privateKeyPem, String certificatePem) {
        try {
            storeKeyPair(alias, PemUtils.parsePrivateKey(privateKeyPem), PemUtils.parseCertificate(certificatePem));
        } catch (Exception e) {
            throw new RuntimeException("Failed to store PEM key pair", e);
        }
    }

    public synchronized boolean hasKey(EmailAddress emailAddress) {
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
        if (keyStorePath == null) {
            return;
        }
        Path tempFile = null;
        try {
            Path parent = keyStorePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            tempFile = Files.createTempFile(parent, keyStorePath.getFileName().toString(), ".tmp");
            try (OutputStream out = Files.newOutputStream(
                    tempFile,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                keyStore.store(out, keyStorePassword);
            }
            setOwnerOnlyFilePermissions(tempFile);
            try {
                Files.move(tempFile, keyStorePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, keyStorePath, StandardCopyOption.REPLACE_EXISTING);
            }
            setOwnerOnlyFilePermissions(keyStorePath);
        } catch (Exception e) {
            log.error("Failed to save keystore: {}", e.getMessage());
            throw new RuntimeException("Failed to save keystore", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    log.debug("Failed to delete temporary keystore file {}: {}", tempFile, e.getMessage());
                }
            }
        }
    }

    private void setOwnerOnlyFilePermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException e) {
            log.debug("POSIX permissions are not supported for keystore {}", path);
        } catch (Exception e) {
            log.debug("Failed to set owner-only permissions for keystore {}: {}", path, e.getMessage());
        }
    }

    private static void ensureBouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
