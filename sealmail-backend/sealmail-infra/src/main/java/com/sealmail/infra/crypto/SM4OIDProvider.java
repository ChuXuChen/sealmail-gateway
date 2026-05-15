package com.sealmail.infra.crypto;

import java.security.Provider;

/**
 * 为 SM4 CBC OID (1.2.156.10197.1.104.2) 提供 JCE Cipher 映射。
 *
 * BouncyCastle 的 EnvelopedDataHelper 在 CMS/SMIME 解密时通过
 * Cipher.getInstance(oid) 解析内容加密算法。由于 BC 未将 SM4 CBC OID
 * 注册为可直接实例化的 Cipher 算法名，此 Provider 将其映射到 BC 的
 * SM4 CBC 实现，使 SM4 内容加密在国密 S/MIME 中可用。
 */
public class SM4OIDProvider extends Provider {
    public SM4OIDProvider() {
        super("SM4OID", "1.0", "SM4 OID Cipher Provider");
        // 将 SM4 CBC OID 映射到自定义的 CipherSpi 包装类
        put("Cipher.1.2.156.10197.1.104.2",
            "com.sealmail.infra.crypto.SM4CBCCipherSpi");
        // 注册 AlgorithmParameters 别名，使 CMS/SMIME 解密时可解析 SM4 IV 参数
        put("Alg.Alias.AlgorithmParameters.1.2.156.10197.1.104.2", "SM4");
    }
}
