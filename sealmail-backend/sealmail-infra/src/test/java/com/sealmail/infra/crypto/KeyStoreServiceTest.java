package com.sealmail.infra.crypto;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyStoreServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void configuredPersistentKeystoreRequiresPassword() {
        Path keyStorePath = tempDir.resolve("sealmail-cert-keys.p12");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new KeyStoreService(keyStorePath.toString(), ""));

        assertTrue(error.getMessage().contains("SEALMAIL_KEYSTORE_PASSWORD"));
    }

    @Test
    void createsMissingPersistentKeystore() {
        Path keyStorePath = tempDir.resolve("sealmail-cert-keys.p12");

        assertDoesNotThrow(() -> new KeyStoreService(keyStorePath.toString(), "changeit"));

        assertTrue(Files.isRegularFile(keyStorePath));
    }

    @Test
    void existingPersistentKeystoreWithWrongPasswordFailsWithoutReplacingFile() throws Exception {
        Path keyStorePath = tempDir.resolve("sealmail-cert-keys.p12");
        createEmptyPkcs12(keyStorePath, "correct-password".toCharArray());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new KeyStoreService(keyStorePath.toString(), "wrong-password"));

        assertTrue(error.getMessage().contains("Failed to load configured keystore"));
        assertDoesNotThrow(() -> new KeyStoreService(keyStorePath.toString(), "correct-password"));
    }

    @Test
    void existingPersistentKeystoreWithSm2CertificateLoads() throws Exception {
        Path keyStorePath = tempDir.resolve("sealmail-cert-keys.p12");
        createSm2Pkcs12(keyStorePath, "changeit".toCharArray());

        KeyStoreService service = new KeyStoreService(keyStorePath.toString(), "changeit");

        assertNotNull(service.getPrivateKeyByAlias("sm2"));
    }

    private void createEmptyPkcs12(Path keyStorePath, char[] password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, password);
        try (OutputStream out = Files.newOutputStream(keyStorePath)) {
            keyStore.store(out, password);
        }
    }

    private void createSm2Pkcs12(Path keyStorePath, char[] password) throws Exception {
        ensureBouncyCastleProvider();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec("sm2p256v1"));
        KeyPair keyPair = generator.generateKeyPair();
        X509Certificate certificate = generateSm2Certificate(keyPair);

        KeyStore keyStore = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
        keyStore.load(null, password);
        keyStore.setKeyEntry("sm2", keyPair.getPrivate(), password, new java.security.cert.Certificate[]{certificate});
        try (OutputStream out = Files.newOutputStream(keyStorePath)) {
            keyStore.store(out, password);
        }
    }

    private X509Certificate generateSm2Certificate(KeyPair keyPair) throws Exception {
        X500Name name = new X500Name("CN=SM2 Test, O=SealMail, E=sm2@test.com");
        long now = System.currentTimeMillis();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name,
                BigInteger.valueOf(now),
                new Date(now),
                new Date(now + 365 * 24 * 60 * 60 * 1000L),
                name,
                keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SM3withSM2")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);
    }

    private void ensureBouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
