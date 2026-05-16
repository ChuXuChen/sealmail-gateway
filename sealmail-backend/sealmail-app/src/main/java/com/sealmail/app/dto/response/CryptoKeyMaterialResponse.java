package com.sealmail.app.dto.response;

public record CryptoKeyMaterialResponse(
        String algorithm,
        String privateKey,
        String certificate,
        String publicKeyFormat,
        String status
) {
}
