package com.sealmail.infra.crypto;

import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.GenericKey;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.operator.jcajce.JceGenericKey;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.OutputStream;
import java.security.SecureRandom;

class ConfigurableContentOutputEncryptor implements OutputEncryptor {

    private final AlgorithmIdentifier algorithmIdentifier;
    private final SecretKeySpec key;
    private final Cipher cipher;

    ConfigurableContentOutputEncryptor(SmimeAlgorithmSuite suite, SecureRandom random) throws Exception {
        if (suite.contentKeySizeBits() <= 0 || suite.contentKeySizeBits() % 8 != 0) {
            throw new IllegalArgumentException("Invalid content key size: " + suite.contentKeySizeBits());
        }

        byte[] keyBytes = new byte[suite.contentKeySizeBits() / 8];
        random.nextBytes(keyBytes);
        this.key = new SecretKeySpec(keyBytes, suite.contentKeyAlgorithm());

        byte[] iv = new byte[16];
        random.nextBytes(iv);
        this.cipher = Cipher.getInstance(suite.contentCipher(), BouncyCastleProvider.PROVIDER_NAME);
        this.cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        this.algorithmIdentifier = new AlgorithmIdentifier(
                suite.contentEncryptionAlgorithm(),
                new DEROctetString(iv));
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
