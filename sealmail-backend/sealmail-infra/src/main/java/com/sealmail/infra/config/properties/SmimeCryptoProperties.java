package com.sealmail.infra.config.properties;

import com.sealmail.infra.crypto.ContentParameterEncoding;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "sealmail.smime.crypto")
public class SmimeCryptoProperties {

    private String defaultStandardSuite = "STANDARD_AES_256_CBC";
    private String defaultGmSuite = "GM_SM4_CBC";
    private Map<String, Suite> suites = Map.of();

    public String getDefaultStandardSuite() {
        return defaultStandardSuite;
    }

    public void setDefaultStandardSuite(String defaultStandardSuite) {
        this.defaultStandardSuite = defaultStandardSuite;
    }

    public String getDefaultGmSuite() {
        return defaultGmSuite;
    }

    public void setDefaultGmSuite(String defaultGmSuite) {
        this.defaultGmSuite = defaultGmSuite;
    }

    public Map<String, Suite> getSuites() {
        return suites;
    }

    public void setSuites(Map<String, Suite> suites) {
        this.suites = suites;
    }

    public static class Suite {
        private String id;
        private String displayName;
        private String profile;
        private String recipientKeyAlgorithmOid;
        private String recipientKeyCipher;
        private String contentEncryptionAlgorithmOid;
        private String contentCipher;
        private String contentKeyAlgorithm;
        private int contentKeySizeBits;
        private ContentParameterEncoding contentParameterEncoding;
        private String signatureAlgorithm;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getProfile() {
            return profile;
        }

        public void setProfile(String profile) {
            this.profile = profile;
        }

        public String getRecipientKeyAlgorithmOid() {
            return recipientKeyAlgorithmOid;
        }

        public void setRecipientKeyAlgorithmOid(String recipientKeyAlgorithmOid) {
            this.recipientKeyAlgorithmOid = recipientKeyAlgorithmOid;
        }

        public String getRecipientKeyCipher() {
            return recipientKeyCipher;
        }

        public void setRecipientKeyCipher(String recipientKeyCipher) {
            this.recipientKeyCipher = recipientKeyCipher;
        }

        public String getContentEncryptionAlgorithmOid() {
            return contentEncryptionAlgorithmOid;
        }

        public void setContentEncryptionAlgorithmOid(String contentEncryptionAlgorithmOid) {
            this.contentEncryptionAlgorithmOid = contentEncryptionAlgorithmOid;
        }

        public String getContentCipher() {
            return contentCipher;
        }

        public void setContentCipher(String contentCipher) {
            this.contentCipher = contentCipher;
        }

        public String getContentKeyAlgorithm() {
            return contentKeyAlgorithm;
        }

        public void setContentKeyAlgorithm(String contentKeyAlgorithm) {
            this.contentKeyAlgorithm = contentKeyAlgorithm;
        }

        public int getContentKeySizeBits() {
            return contentKeySizeBits;
        }

        public void setContentKeySizeBits(int contentKeySizeBits) {
            this.contentKeySizeBits = contentKeySizeBits;
        }

        public ContentParameterEncoding getContentParameterEncoding() {
            return contentParameterEncoding;
        }

        public void setContentParameterEncoding(ContentParameterEncoding contentParameterEncoding) {
            this.contentParameterEncoding = contentParameterEncoding;
        }

        public String getSignatureAlgorithm() {
            return signatureAlgorithm;
        }

        public void setSignatureAlgorithm(String signatureAlgorithm) {
            this.signatureAlgorithm = signatureAlgorithm;
        }
    }
}
