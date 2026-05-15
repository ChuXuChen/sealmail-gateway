package com.sealmail.domain.policy;

/**
 * 域名级别的加密算法偏好。
 * 控制 S/MIME 签名和加密时优先使用国密还是国际算法。
 */
public enum PreferredAlgorithm {
    AUTO,        // 自动选择：优先国密，国密不可用则回退国际
    GM_ONLY,     // 强制国密：只使用 SM2/SM3/SM4，找不到国密证书时报错
    STANDARD_ONLY // 强制国际：只使用 RSA/AES/SHA256
}
