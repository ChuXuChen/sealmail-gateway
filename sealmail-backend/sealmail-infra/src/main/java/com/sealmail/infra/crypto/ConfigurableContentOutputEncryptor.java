package com.sealmail.infra.crypto;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.cms.GCMParameters;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.GenericKey;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.operator.jcajce.JceGenericKey;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.OutputStream;
import java.security.SecureRandom;

class ConfigurableContentOutputEncryptor implements OutputEncryptor {

    private static final int GCM_NONCE_SIZE_BYTES = 12;
    private static final int GCM_TAG_SIZE_BYTES = 16;

    private final AlgorithmIdentifier algorithmIdentifier;
    private final SecretKeySpec key;
    private final Cipher cipher;

    ConfigurableContentOutputEncryptor(SmimeAlgorithmSuite suite, SecureRandom random) throws Exception {
        ContentParameterEncoding contentParameterEncoding = suite.contentParameterEncoding();
        byte[] keyBytes = new byte[suite.contentKeySizeBits() / 8];
        random.nextBytes(keyBytes);
        this.key = new SecretKeySpec(keyBytes, suite.contentKeyAlgorithm());

        this.cipher = Cipher.getInstance(suite.contentCipher(), BouncyCastleProvider.PROVIDER_NAME);
        ASN1Encodable parameters;
        if (contentParameterEncoding == ContentParameterEncoding.GCM_PARAMETERS) {
            byte[] nonce = new byte[GCM_NONCE_SIZE_BYTES];
            random.nextBytes(nonce);
            this.cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_SIZE_BYTES * 8, nonce));
            parameters = new GCMParameters(nonce, GCM_TAG_SIZE_BYTES);
        } else {
            byte[] iv = new byte[16];
            random.nextBytes(iv);
            this.cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
            parameters = new DEROctetString(iv);
        }
        this.algorithmIdentifier = new AlgorithmIdentifier(
                suite.contentEncryptionAlgorithm(),
                parameters);
    }

    @Override
    public AlgorithmIdentifier getAlgorithmIdentifier() {
        return algorithmIdentifier;
    }

    @Override
    public OutputStream getOutputStream(OutputStream out) {
        return new CipherOutputStream(out, cipher);
    }

    @Override
    public GenericKey getKey() {
        return new JceGenericKey(algorithmIdentifier, key);
    }
}
