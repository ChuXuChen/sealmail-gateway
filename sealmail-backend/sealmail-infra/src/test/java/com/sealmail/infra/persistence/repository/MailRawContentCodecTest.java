package com.sealmail.infra.persistence.repository;

import com.sealmail.domain.config.SecretReferenceResolver;
import com.sealmail.infra.config.properties.RawContentStorageProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailRawContentCodecTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";

    @Test
    void encryptsNewRawContentAndKeepsHashOfPlaintext() {
        MailRawContentCodec codec = codec(KEY, true);
        byte[] raw = "Subject: secret\r\n\r\nbody".getBytes(StandardCharsets.UTF_8);

        MailRawContentCodec.EncodedRawContent encoded = codec.encode(raw);

        assertThat(encoded.content()).startsWith(MailRawContentCodec.ENCRYPTED_PREFIX);
        assertThat(encoded.content()).doesNotContain(Base64.getEncoder().encodeToString(raw));
        assertThat(encoded.contentType()).isEqualTo(MailRawContentCodec.ENCRYPTED_CONTENT_TYPE);
        assertThat(encoded.sizeBytes()).isEqualTo(raw.length);
        assertThat(encoded.sha256()).hasSize(64);
        assertThat(codec.decode(encoded.content())).isEqualTo(raw);
    }

    @Test
    void readsLegacyBase64Rows() {
        MailRawContentCodec codec = codec(KEY, true);
        byte[] raw = "legacy raw mail".getBytes(StandardCharsets.UTF_8);

        assertThat(codec.decode(Base64.getEncoder().encodeToString(raw))).isEqualTo(raw);
    }

    @Test
    void failsFastWhenEncryptionKeyIsMissing() {
        MailRawContentCodec codec = codec(null, true);

        assertThatThrownBy(() -> codec.encode("raw".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Raw mail content encryption key is not configured");
    }

    @Test
    void validatesEncryptionKeyAtStartup() {
        MailRawContentCodec codec = codec(null, true);

        assertThatThrownBy(codec::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Raw mail content encryption key is not configured");
    }

    @Test
    void legacyModeStillEncodesBase64WhenExplicitlyDisabled() {
        MailRawContentCodec codec = codec(null, false);
        byte[] raw = "raw".getBytes(StandardCharsets.UTF_8);

        MailRawContentCodec.EncodedRawContent encoded = codec.encode(raw);

        assertThat(encoded.content()).isEqualTo(Base64.getEncoder().encodeToString(raw));
        assertThat(encoded.contentType()).isEqualTo(MailRawContentCodec.LEGACY_CONTENT_TYPE);
        assertThat(codec.decode(encoded.content())).isEqualTo(raw);
    }

    @Test
    void acceptsExplicitBase64KeyPrefix() {
        String key = "base64:" + Base64.getEncoder().encodeToString(KEY.getBytes(StandardCharsets.UTF_8));
        MailRawContentCodec codec = codec(key, true);
        byte[] raw = "raw".getBytes(StandardCharsets.UTF_8);

        MailRawContentCodec.EncodedRawContent encoded = codec.encode(raw);

        assertThat(codec.decode(encoded.content())).isEqualTo(raw);
    }

    private static MailRawContentCodec codec(String key, boolean encryptionEnabled) {
        RawContentStorageProperties properties = new RawContentStorageProperties();
        properties.getEncryption().setEnabled(encryptionEnabled);
        properties.getEncryption().setKeyRef("test:key");
        SecretReferenceResolver resolver = secretRef -> key;
        return new MailRawContentCodec(properties, resolver);
    }
}
