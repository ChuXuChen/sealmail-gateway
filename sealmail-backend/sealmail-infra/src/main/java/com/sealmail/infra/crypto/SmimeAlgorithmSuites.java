package com.sealmail.infra.crypto;

import com.sealmail.domain.certificate.spi.SMIMEEncryptionSuite;
import com.sealmail.domain.mailsecurity.CryptoProfile;
import com.sealmail.infra.config.properties.SmimeCryptoProperties;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.gm.GMObjectIdentifiers;
import org.bouncycastle.cms.CMSAlgorithm;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SmimeAlgorithmSuites {

    public static final String STANDARD_AES_256_GCM = "STANDARD_AES_256_GCM";
    public static final String STANDARD_AES_128_GCM = "STANDARD_AES_128_GCM";
    public static final String STANDARD_AES_256_CBC = "STANDARD_AES_256_CBC";
    public static final String STANDARD_AES_128_CBC = "STANDARD_AES_128_CBC";
    public static final String GM_SM4_CBC = "GM_SM4_CBC";
    public static final String GM_SM4_GCM = "GM_SM4_GCM";

    private final Map<String, SmimeAlgorithmSuite> suitesById;
    private final Map<CryptoProfile, String> defaultSuiteIds = new EnumMap<>(CryptoProfile.class);

    public SmimeAlgorithmSuites(SmimeCryptoProperties properties) {
        this.suitesById = withOverrides(builtInSuites(), properties.getSuites());
        defaultSuiteIds.put(CryptoProfile.STANDARD,
                requireSuiteId(properties.getDefaultStandardSuite(), STANDARD_AES_256_CBC));
        defaultSuiteIds.put(CryptoProfile.GM,
                requireSuiteId(properties.getDefaultGmSuite(), GM_SM4_CBC));
        validateDefaults();
    }

    public static SmimeAlgorithmSuites defaults() {
        return new SmimeAlgorithmSuites(new SmimeCryptoProperties());
    }

    public SmimeAlgorithmSuite get(SMIMEEncryptionSuite suite) {
        return get(toProfile(suite));
    }

    public SmimeAlgorithmSuite get(CryptoProfile profile) {
        String suiteId = defaultSuiteIds.get(profile);
        if (suiteId == null) {
            throw new IllegalArgumentException("Concrete crypto profile is required");
        }
        return get(suiteId);
    }

    public SmimeAlgorithmSuite get(String suiteId) {
        SmimeAlgorithmSuite suite = suitesById.get(suiteId);
        if (suite == null) {
            throw new IllegalArgumentException("No S/MIME algorithm suite configured for " + suiteId);
        }
        return suite;
    }

    public List<SmimeAlgorithmSuite> all() {
        return List.copyOf(suitesById.values());
    }

    public List<SmimeAlgorithmSuite> forProfile(CryptoProfile profile) {
        return suitesById.values().stream()
                .filter(suite -> suite.profile() == profile)
                .toList();
    }

    private static Map<String, SmimeAlgorithmSuite> withOverrides(
            Map<String, SmimeAlgorithmSuite> builtIns,
            Map<String, SmimeCryptoProperties.Suite> overrides) {
        Map<String, SmimeAlgorithmSuite> suites = new LinkedHashMap<>(builtIns);
        if (overrides == null) {
            return suites;
        }
        overrides.forEach((suiteId, override) -> {
            String effectiveId = hasText(override.getId()) ? override.getId() : suiteId;
            SmimeAlgorithmSuite base = suites.get(effectiveId);
            suites.put(effectiveId, base == null
                    ? SmimeAlgorithmSuite.from(effectiveId, override)
                    : base.merge(effectiveId, override));
        });
        return suites;
    }

    private void validateDefaults() {
        for (CryptoProfile profile : List.of(CryptoProfile.STANDARD, CryptoProfile.GM)) {
            SmimeAlgorithmSuite suite = get(defaultSuiteIds.get(profile));
            if (suite.profile() != profile) {
                throw new IllegalArgumentException("Default " + profile + " S/MIME suite has profile "
                        + suite.profile() + ": " + suite.id());
            }
        }
    }

    private static Map<String, SmimeAlgorithmSuite> builtInSuites() {
        Map<String, SmimeAlgorithmSuite> suites = new LinkedHashMap<>();
        put(suites, suite(
                STANDARD_AES_256_GCM,
                "AES-256-GCM",
                CryptoProfile.STANDARD,
                CMSAlgorithm.RSA_PKCS15,
                "RSA/ECB/PKCS1Padding",
                CMSAlgorithm.AES256_GCM,
                "AES/GCM/NoPadding",
                "AES",
                256,
                ContentParameterEncoding.CMS_BUILDER,
                "SHA256withRSA"));
        put(suites, suite(
                STANDARD_AES_128_GCM,
                "AES-128-GCM",
                CryptoProfile.STANDARD,
                CMSAlgorithm.RSA_PKCS15,
                "RSA/ECB/PKCS1Padding",
                CMSAlgorithm.AES128_GCM,
                "AES/GCM/NoPadding",
                "AES",
                128,
                ContentParameterEncoding.CMS_BUILDER,
                "SHA256withRSA"));
        put(suites, suite(
                STANDARD_AES_256_CBC,
                "AES-256-CBC",
                CryptoProfile.STANDARD,
                CMSAlgorithm.RSA_PKCS15,
                "RSA/ECB/PKCS1Padding",
                CMSAlgorithm.AES256_CBC,
                "AES/CBC/PKCS5Padding",
                "AES",
                256,
                ContentParameterEncoding.CMS_BUILDER,
                "SHA256withRSA"));
        put(suites, suite(
                STANDARD_AES_128_CBC,
                "AES-128-CBC",
                CryptoProfile.STANDARD,
                CMSAlgorithm.RSA_PKCS15,
                "RSA/ECB/PKCS1Padding",
                CMSAlgorithm.AES128_CBC,
                "AES/CBC/PKCS5Padding",
                "AES",
                128,
                ContentParameterEncoding.CMS_BUILDER,
                "SHA256withRSA"));
        put(suites, suite(
                GM_SM4_CBC,
                "SM4-CBC",
                CryptoProfile.GM,
                GMObjectIdentifiers.sm2encrypt_with_sm3,
                "SM2WITHSM3",
                GMObjectIdentifiers.sms4_cbc,
                "SM4/CBC/PKCS7Padding",
                "SM4",
                128,
                ContentParameterEncoding.CBC_IV,
                "SM3withSM2"));
        put(suites, suite(
                GM_SM4_GCM,
                "SM4-GCM",
                CryptoProfile.GM,
                GMObjectIdentifiers.sm2encrypt_with_sm3,
                "SM2WITHSM3",
                GMObjectIdentifiers.sms4_gcm,
                "SM4/GCM/NoPadding",
                "SM4",
                128,
                ContentParameterEncoding.GCM_PARAMETERS,
                "SM3withSM2"));
        return suites;
    }

    private static void put(Map<String, SmimeAlgorithmSuite> suites, SmimeAlgorithmSuite suite) {
        suites.put(suite.id(), suite);
    }

    private static SmimeAlgorithmSuite suite(String id,
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
        return new SmimeAlgorithmSuite(
                id,
                displayName,
                profile,
                recipientKeyAlgorithm,
                recipientKeyCipher,
                contentEncryptionAlgorithm,
                contentCipher,
                contentKeyAlgorithm,
                contentKeySizeBits,
                contentParameterEncoding,
                signatureAlgorithm);
    }

    private static String requireSuiteId(String configured, String fallback) {
        return hasText(configured) ? configured.trim() : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static SMIMEEncryptionSuite toSuite(CryptoProfile profile) {
        if (profile == CryptoProfile.GM) {
            return SMIMEEncryptionSuite.GM;
        }
        if (profile == CryptoProfile.STANDARD) {
            return SMIMEEncryptionSuite.STANDARD;
        }
        throw new IllegalArgumentException("Concrete crypto profile is required");
    }

    public static CryptoProfile toProfile(SMIMEEncryptionSuite suite) {
        return suite == SMIMEEncryptionSuite.GM ? CryptoProfile.GM : CryptoProfile.STANDARD;
    }
}
