package com.sealmail.infra.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    private void createEmptyPkcs12(Path keyStorePath, char[] password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, password);
        try (OutputStream out = Files.newOutputStream(keyStorePath)) {
            keyStore.store(out, password);
        }
    }
}
