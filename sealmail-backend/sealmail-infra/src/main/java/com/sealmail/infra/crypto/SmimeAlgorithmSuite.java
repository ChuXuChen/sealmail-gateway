package com.sealmail.infra.crypto;

import com.sealmail.domain.certificate.spi.SMIMEEncryptionSuite;
import com.sealmail.domain.mailsecurity.CryptoProfile;
import com.sealmail.infra.config.properties.SmimeCryptoProperties;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;

public record SmimeAlgorithmSuite(
        CryptoProfile profile,
        ASN1ObjectIdentifier recipientKeyAlgorithm,
        String recipientKeyCipher,
        ASN1ObjectIdentifier contentEncryptionAlgorithm,
        String contentCipher,
        String contentKeyAlgorithm,
        int contentKeySizeBits,
        String signatureAlgorithm) {

    public static SmimeAlgorithmSuite from(SMIMEEncryptionSuite suite,
                                           SmimeCryptoProperties.Suite properties) {
        return new SmimeAlgorithmSuite(
                SmimeAlgorithmSuites.toProfile(suite),
                new ASN1ObjectIdentifier(required(properties.getRecipientKeyAlgorithmOid(), "recipientKeyAlgorithmOid")),
                required(properties.getRecipientKeyCipher(), "recipientKeyCipher"),
                new ASN1ObjectIdentifier(required(properties.getContentEncryptionAlgorithmOid(), "contentEncryptionAlgorithmOid")),
                required(properties.getContentCipher(), "contentCipher"),
                required(properties.getContentKeyAlgorithm(), "contentKeyAlgorithm"),
                properties.getContentKeySizeBits(),
                required(properties.getSignatureAlgorithm(), "signatureAlgorithm"));
    }

    private static String required(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("S/MIME crypto suite property is blank: " + property);
        }
        return value;
    }
}
