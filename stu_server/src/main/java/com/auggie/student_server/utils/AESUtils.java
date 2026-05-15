package com.auggie.student_server.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * AES加密工具类
 * 用于敏感配置信息的加密存储
 */
public class AESUtils {

    // 加密密钥（生产环境建议配置在环境变量中）
    private static final String SECRET_KEY = "StudentMS2026@Key"; // 16位密钥

    // 加密算法
    private static final String ALGORITHM = "AES";

    // 编码格式
    private static final String CHARSET = "UTF-8";

    /**
     * 加密
     * @param content 待加密内容
     * @return 加密后的Base64字符串
     */
    public static String encrypt(String content) {
        try {
            if (content == null || content.trim().isEmpty()) {
                return null;
            }
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(CHARSET), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(content.getBytes(CHARSET));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("AES加密失败", e);
        }
    }

    /**
     * 解密
     * @param encryptedContent 加密后的Base64字符串
     * @return 解密后的内容
     */
    public static String decrypt(String encryptedContent) {
        try {
            if (encryptedContent == null || encryptedContent.trim().isEmpty()) {
                return null;
            }
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(CHARSET), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedContent));
            return new String(decryptedBytes, CHARSET);
        } catch (Exception e) {
            throw new RuntimeException("AES解密失败", e);
        }
    }

    /**
     * 测试方法
     */
    public static void main(String[] args) {
        String original = "b731f82d-dc13-4b70-a841-999b2faeffe7";
        String encrypted = encrypt(original);
        String decrypted = decrypt(encrypted);

        System.out.println("原文: " + original);
        System.out.println("加密后: " + encrypted);
        System.out.println("解密后: " + decrypted);
        System.out.println("是否一致: " + original.equals(decrypted));
    }
}
