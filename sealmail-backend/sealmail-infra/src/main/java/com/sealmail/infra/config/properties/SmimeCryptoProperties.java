package com.sealmail.infra.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sealmail.smime.crypto")
public class SmimeCryptoProperties {

    private Suite standard = Suite.standardDefaults();
    private Suite gm = Suite.gmDefaults();

    public Suite getStandard() {
        return standard;
    }

    public void setStandard(Suite standard) {
        this.standard = standard;
    }

    public Suite getGm() {
        return gm;
    }

    public void setGm(Suite gm) {
        this.gm = gm;
    }

    public static class Suite {
        private String recipientKeyAlgorithmOid;
        private String recipientKeyCipher;
        private String contentEncryptionAlgorithmOid;
        private String contentCipher;
        private String contentKeyAlgorithm;
        private int contentKeySizeBits;
        private String signatureAlgorithm;

        static Suite standardDefaults() {
            Suite suite = new Suite();
            suite.setRecipientKeyAlgorithmOid("1.2.840.113549.1.1.1");
            suite.setRecipientKeyCipher("RSA/ECB/PKCS1Padding");
            suite.setContentEncryptionAlgorithmOid("2.16.840.1.101.3.4.1.42");
            suite.setContentCipher("AES/CBC/PKCS5Padding");
            suite.setContentKeyAlgorithm("AES");
            suite.setContentKeySizeBits(256);
            suite.setSignatureAlgorithm("SHA256withRSA");
            return suite;
        }

        static Suite gmDefaults() {
            Suite suite = new Suite();
            suite.setRecipientKeyAlgorithmOid("1.2.156.10197.1.301.3.2.1");
            suite.setRecipientKeyCipher("SM2WITHSM3");
            suite.setContentEncryptionAlgorithmOid("1.2.156.10197.1.104.2");
            suite.setContentCipher("SM4/CBC/PKCS7Padding");
            suite.setContentKeyAlgorithm("SM4");
            suite.setContentKeySizeBits(128);
            suite.setSignatureAlgorithm("SM3withSM2");
            return suite;
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

        public String getSignatureAlgorithm() {
            return signatureAlgorithm;
        }

        public void setSignatureAlgorithm(String signatureAlgorithm) {
            this.signatureAlgorithm = signatureAlgorithm;
        }
    }
}
