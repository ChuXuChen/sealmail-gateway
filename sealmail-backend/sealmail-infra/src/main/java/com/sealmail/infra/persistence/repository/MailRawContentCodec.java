package com.sealmail.infra.persistence.repository;

import com.sealmail.domain.config.SecretReferenceResolver;
import com.sealmail.infra.config.properties.RawContentStorageProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class MailRawContentCodec {

    static final String ENCRYPTED_PREFIX = "enc:v1:";
    static final String ENCRYPTED_CONTENT_TYPE = "message/rfc822; storage=enc-v1";
    static final String LEGACY_CONTENT_TYPE = "message/rfc822;base64";

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final RawContentStorageProperties properties;
    private final SecretReferenceResolver secretReferenceResolver;
    private final SecureRandom secureRandom;

    public MailRawContentCodec(RawContentStorageProperties properties,
                               SecretReferenceResolver secretReferenceResolver) {
        this(properties, secretReferenceResolver, new SecureRandom());
    }

    MailRawContentCodec(RawContentStorageProperties properties,
                        SecretReferenceResolver secretReferenceResolver,
                        SecureRandom secureRandom) {
        this.properties = properties;
        this.secretReferenceResolver = secretReferenceResolver;
        this.secureRandom = secureRandom;
    }

    public EncodedRawContent encode(byte[] rawContent) {
        byte[] content = rawContent == null ? new byte[0] : rawContent;
        if (!properties.getEncryption().isEnabled()) {
            return new EncodedRawContent(
                    Base64.getEncoder().encodeToString(content),
                    LEGACY_CONTENT_TYPE,
                    sha256Hex(content),
                    content.length);
        }
        byte[] iv = new byte[GCM_IV_BYTES];
        secureRandom.nextBytes(iv);
        byte[] encrypted = encrypt(content, iv);
        ByteBuffer payload = ByteBuffer.allocate(iv.length + encrypted.length);
        payload.put(iv);
        payload.put(encrypted);
        return new EncodedRawContent(
                ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(payload.array()),
                ENCRYPTED_CONTENT_TYPE,
                sha256Hex(content),
                content.length);
    }

    public byte[] decode(String storedContent) {
        if (storedContent == null || storedContent.isBlank()) {
            return new byte[0];
        }
        String trimmed = storedContent.trim();
        if (!trimmed.startsWith(ENCRYPTED_PREFIX)) {
            return Base64.getDecoder().decode(trimmed);
        }
        byte[] payload = Base64.getDecoder().decode(trimmed.substring(ENCRYPTED_PREFIX.length()));
        if (payload.length <= GCM_IV_BYTES) {
            throw new IllegalStateException("Encrypted raw mail content payload is truncated");
        }
        byte[] iv = new byte[GCM_IV_BYTES];
        byte[] encrypted = new byte[payload.length - GCM_IV_BYTES];
        System.arraycopy(payload, 0, iv, 0, iv.length);
        System.arraycopy(payload, iv.length, encrypted, 0, encrypted.length);
        return decrypt(encrypted, iv);
    }

    private byte[] encrypt(byte[] content, byte[] iv) {
        SecretKeySpec key = keySpec();
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(content);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt raw mail content", e);
        }
    }

    private byte[] decrypt(byte[] encrypted, byte[] iv) {
        SecretKeySpec key = keySpec();
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt raw mail content", e);
        }
    }

    private SecretKeySpec keySpec() {
        String keyRef = properties.getEncryption().getKeyRef();
        String secret = secretReferenceResolver.resolve(keyRef);
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Raw mail content encryption key is not configured. Set sealmail.storage.raw-content.encryption.key-ref to a resolvable secret reference.");
        }
        return new SecretKeySpec(normalizeKey(secret), "AES");
    }

    private byte[] normalizeKey(String configuredSecret) {
        String secret = configuredSecret.trim();
        if (secret.startsWith("base64:")) {
            byte[] decoded = Base64.getDecoder().decode(secret.substring("base64:".length()));
            if (!isSupportedAesKeyLength(decoded.length)) {
                throw new IllegalStateException("Raw mail content encryption key must decode to 16, 24 or 32 bytes");
            }
            return decoded;
        }
        byte[] utf8 = secret.getBytes(StandardCharsets.UTF_8);
        if (isSupportedAesKeyLength(utf8.length)) {
            return utf8;
        }
        byte[] decoded = tryDecodeBase64(secret);
        if (isSupportedAesKeyLength(decoded.length)) {
            return decoded;
        }
        return sha256(utf8);
    }

    private byte[] tryDecodeBase64(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    private boolean isSupportedAesKeyLength(int length) {
        return length == 16 || length == 24 || length == 32;
    }

    private String sha256Hex(byte[] bytes) {
        return HexFormat.of().formatHex(sha256(bytes));
    }

    private byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is not available", e);
        }
    }

    public record EncodedRawContent(
            String content,
            String contentType,
            String sha256,
            long sizeBytes
    ) {
    }
}
