package com.sealmail.infra.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;

/**
 * 自定义 CipherSpi，将 SM4 CBC OID (1.2.156.10197.1.104.2) 映射到 BC 的 SM4/CBC/PKCS7Padding。
 *
 * BC 1.78.1 没有公开的 SM4$CBC 类，但支持 Cipher.getInstance("SM4/CBC/PKCS7Padding", "BC")。
 * 此包装类使 SM4 OID 可在 CMS/SMIME 的 EnvelopedDataHelper 中通过 Cipher.getInstance(oid) 解析。
 */
public class SM4CBCCipherSpi extends CipherSpi {
    private Cipher delegate;

    @Override
    protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
        // ignored - delegate is already CBC
    }

    @Override
    protected void engineSetPadding(String padding) throws NoSuchPaddingException {
        // ignored - delegate is already PKCS7Padding
    }

    @Override
    protected int engineGetBlockSize() {
        return delegate != null ? delegate.getBlockSize() : 16;
    }

    @Override
    protected int engineGetOutputSize(int inputLen) {
        return delegate.getOutputSize(inputLen);
    }

    @Override
    protected byte[] engineGetIV() {
        return delegate.getIV();
    }

    @Override
    protected AlgorithmParameters engineGetParameters() {
        return delegate.getParameters();
    }

    @Override
    protected void engineInit(int opmode, Key key, SecureRandom random) throws InvalidKeyException {
        try {
            delegate = Cipher.getInstance("SM4/CBC/PKCS7Padding", BouncyCastleProvider.PROVIDER_NAME);
            delegate.init(opmode, key, random);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | NoSuchProviderException e) {
            throw new InvalidKeyException(e);
        }
    }

    @Override
    protected void engineInit(int opmode, Key key, AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        try {
            delegate = Cipher.getInstance("SM4/CBC/PKCS7Padding", BouncyCastleProvider.PROVIDER_NAME);
            delegate.init(opmode, key, params, random);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | NoSuchProviderException e) {
            throw new InvalidKeyException(e);
        }
    }

    @Override
    protected void engineInit(int opmode, Key key, AlgorithmParameters params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        try {
            delegate = Cipher.getInstance("SM4/CBC/PKCS7Padding", BouncyCastleProvider.PROVIDER_NAME);
            delegate.init(opmode, key, params, random);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | NoSuchProviderException e) {
            throw new InvalidKeyException(e);
        }
    }

    @Override
    protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
        if (input == null || inputLen == 0) {
            return new byte[0];
        }
        return delegate.update(input, inputOffset, inputLen);
    }

    @Override
    protected int engineUpdate(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset)
            throws ShortBufferException {
        if (input == null || inputLen == 0) {
            return 0;
        }
        return delegate.update(input, inputOffset, inputLen, output, outputOffset);
    }

    @Override
    protected byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen)
            throws IllegalBlockSizeException, BadPaddingException {
        if (input == null || inputLen == 0) {
            return delegate.doFinal();
        }
        return delegate.doFinal(input, inputOffset, inputLen);
    }

    @Override
    protected int engineDoFinal(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset)
            throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        if (input == null || inputLen == 0) {
            byte[] result = delegate.doFinal();
            if (result != null) {
                System.arraycopy(result, 0, output, outputOffset, result.length);
                return result.length;
            }
            return 0;
        }
        return delegate.doFinal(input, inputOffset, inputLen, output, outputOffset);
    }
}
