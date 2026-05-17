package com.sealmail.infra.crypto;

import com.sealmail.domain.mailsecurity.CryptoProfile;
import com.sealmail.infra.config.properties.SmimeCryptoProperties;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;

public record SmimeAlgorithmSuite(
        String id,
        String displayName,
        CryptoProfile profile,
        ASN1ObjectIdentifier recipientKeyAlgorithm,
        String recipientKeyCipher,
        ASN1ObjectIdentifier contentEncryptionAlgorithm,
        String contentCipher,
        String contentKeyAlgorithm,
        int contentKeySizeBits,
        ContentParameterEncoding contentParameterEncoding,
        String signatureAlgorithm) {

    static SmimeAlgorithmSuite from(SmimeCryptoProperties.Suite properties) {
        return from(properties.getId(), properties);
    }

    static SmimeAlgorithmSuite from(String id, SmimeCryptoProperties.Suite properties) {
        ContentParameterEncoding contentParameterEncoding = properties.getContentParameterEncoding() == null
                ? ContentParameterEncoding.CMS_BUILDER
                : properties.getContentParameterEncoding();
        int contentKeySizeBits = properties.getContentKeySizeBits();
        if (contentKeySizeBits <= 0 || contentKeySizeBits % 8 != 0) {
            throw new IllegalArgumentException("Invalid S/MIME content key size: " + contentKeySizeBits);
        }
        return new SmimeAlgorithmSuite(
                required(id, "id"),
                required(properties.getDisplayName(), "displayName"),
                profile(properties.getProfile()),
                new ASN1ObjectIdentifier(required(properties.getRecipientKeyAlgorithmOid(), "recipientKeyAlgorithmOid")),
                required(properties.getRecipientKeyCipher(), "recipientKeyCipher"),
                new ASN1ObjectIdentifier(required(properties.getContentEncryptionAlgorithmOid(), "contentEncryptionAlgorithmOid")),
                required(properties.getContentCipher(), "contentCipher"),
                required(properties.getContentKeyAlgorithm(), "contentKeyAlgorithm"),
                contentKeySizeBits,
                contentParameterEncoding,
                required(properties.getSignatureAlgorithm(), "signatureAlgorithm"));
    }

    SmimeCryptoProperties.Suite toProperties() {
        SmimeCryptoProperties.Suite properties = new SmimeCryptoProperties.Suite();
        properties.setId(id);
        properties.setDisplayName(displayName);
        properties.setProfile(profile.name());
        properties.setRecipientKeyAlgorithmOid(recipientKeyAlgorithm.getId());
        properties.setRecipientKeyCipher(recipientKeyCipher);
        properties.setContentEncryptionAlgorithmOid(contentEncryptionAlgorithm.getId());
        properties.setContentCipher(contentCipher);
        properties.setContentKeyAlgorithm(contentKeyAlgorithm);
        properties.setContentKeySizeBits(contentKeySizeBits);
        properties.setContentParameterEncoding(contentParameterEncoding);
        properties.setSignatureAlgorithm(signatureAlgorithm);
        return properties;
    }

    SmimeAlgorithmSuite merge(String suiteId, SmimeCryptoProperties.Suite override) {
        SmimeCryptoProperties.Suite merged = toProperties();
        merged.setId(suiteId);
        if (hasText(override.getDisplayName())) {
            merged.setDisplayName(override.getDisplayName());
        }
        if (hasText(override.getProfile())) {
            merged.setProfile(override.getProfile());
        }
        if (hasText(override.getRecipientKeyAlgorithmOid())) {
            merged.setRecipientKeyAlgorithmOid(override.getRecipientKeyAlgorithmOid());
        }
        if (hasText(override.getRecipientKeyCipher())) {
            merged.setRecipientKeyCipher(override.getRecipientKeyCipher());
        }
        if (hasText(override.getContentEncryptionAlgorithmOid())) {
            merged.setContentEncryptionAlgorithmOid(override.getContentEncryptionAlgorithmOid());
        }
        if (hasText(override.getContentCipher())) {
            merged.setContentCipher(override.getContentCipher());
        }
        if (hasText(override.getContentKeyAlgorithm())) {
            merged.setContentKeyAlgorithm(override.getContentKeyAlgorithm());
        }
        if (override.getContentKeySizeBits() > 0) {
            merged.setContentKeySizeBits(override.getContentKeySizeBits());
        }
        if (override.getContentParameterEncoding() != null) {
            merged.setContentParameterEncoding(override.getContentParameterEncoding());
        }
        if (hasText(override.getSignatureAlgorithm())) {
            merged.setSignatureAlgorithm(override.getSignatureAlgorithm());
        }
        return from(merged);
    }

    private static CryptoProfile profile(String value) {
        String profile = required(value, "profile").trim().toUpperCase(java.util.Locale.ROOT);
        try {
            CryptoProfile cryptoProfile = CryptoProfile.valueOf(profile);
            if (!cryptoProfile.isConcrete()) {
                throw new IllegalArgumentException("S/MIME crypto suite profile must be STANDARD or GM: " + value);
            }
            return cryptoProfile;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("S/MIME crypto suite profile must be STANDARD or GM: " + value, e);
        }
    }

    private static String required(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("S/MIME crypto suite property is blank: " + property);
        }
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
