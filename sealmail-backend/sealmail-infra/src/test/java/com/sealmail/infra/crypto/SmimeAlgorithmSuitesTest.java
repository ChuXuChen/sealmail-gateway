package com.sealmail.infra.crypto;

import com.sealmail.domain.mailsecurity.CryptoProfile;
import com.sealmail.infra.config.properties.SmimeCryptoProperties;
import org.bouncycastle.asn1.gm.GMObjectIdentifiers;
import org.bouncycastle.cms.CMSAlgorithm;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmimeAlgorithmSuitesTest {

    @Test
    void defaultsExposeCommonStandardAndGmSuites() {
        SmimeAlgorithmSuites suites = SmimeAlgorithmSuites.defaults();

        assertEquals(6, suites.all().size());
        assertEquals(CMSAlgorithm.AES256_CBC, suites.get(CryptoProfile.STANDARD).contentEncryptionAlgorithm());
        assertEquals(GMObjectIdentifiers.sms4_cbc, suites.get(CryptoProfile.GM).contentEncryptionAlgorithm());
        assertEquals(ContentParameterEncoding.CBC_IV,
                suites.get(SmimeAlgorithmSuites.GM_SM4_CBC).contentParameterEncoding());
        assertEquals(ContentParameterEncoding.GCM_PARAMETERS,
                suites.get(SmimeAlgorithmSuites.GM_SM4_GCM).contentParameterEncoding());
    }

    @Test
    void builtInSuitesCanBePartiallyOverridden() {
        SmimeCryptoProperties properties = new SmimeCryptoProperties();
        SmimeCryptoProperties.Suite override = new SmimeCryptoProperties.Suite();
        override.setDisplayName("SM4-CBC local");
        properties.setSuites(Map.of(SmimeAlgorithmSuites.GM_SM4_CBC, override));

        SmimeAlgorithmSuite gmSuite = new SmimeAlgorithmSuites(properties).get(CryptoProfile.GM);

        assertEquals("SM4-CBC local", gmSuite.displayName());
        assertEquals(GMObjectIdentifiers.sms4_cbc, gmSuite.contentEncryptionAlgorithm());
        assertEquals(ContentParameterEncoding.CBC_IV, gmSuite.contentParameterEncoding());
    }

    @Test
    void customSuiteCanBecomeStandardDefault() {
        SmimeCryptoProperties properties = new SmimeCryptoProperties();
        properties.setDefaultStandardSuite("LOCAL_AES_192_CBC");

        SmimeCryptoProperties.Suite custom = new SmimeCryptoProperties.Suite();
        custom.setDisplayName("AES-192-CBC");
        custom.setProfile(CryptoProfile.STANDARD.name());
        custom.setRecipientKeyAlgorithmOid(CMSAlgorithm.RSA_PKCS15.getId());
        custom.setRecipientKeyCipher("RSA/ECB/PKCS1Padding");
        custom.setContentEncryptionAlgorithmOid(CMSAlgorithm.AES192_CBC.getId());
        custom.setContentCipher("AES/CBC/PKCS5Padding");
        custom.setContentKeyAlgorithm("AES");
        custom.setContentKeySizeBits(192);
        custom.setSignatureAlgorithm("SHA256withRSA");
        properties.setSuites(Map.of("LOCAL_AES_192_CBC", custom));

        SmimeAlgorithmSuite standardSuite = new SmimeAlgorithmSuites(properties).get(CryptoProfile.STANDARD);

        assertEquals("LOCAL_AES_192_CBC", standardSuite.id());
        assertEquals(CMSAlgorithm.AES192_CBC, standardSuite.contentEncryptionAlgorithm());
        assertEquals(ContentParameterEncoding.CMS_BUILDER, standardSuite.contentParameterEncoding());
    }

    @Test
    void defaultSuiteMustMatchProfile() {
        SmimeCryptoProperties properties = new SmimeCryptoProperties();
        properties.setDefaultStandardSuite(SmimeAlgorithmSuites.GM_SM4_CBC);

        assertThrows(IllegalArgumentException.class, () -> new SmimeAlgorithmSuites(properties));
    }
}
