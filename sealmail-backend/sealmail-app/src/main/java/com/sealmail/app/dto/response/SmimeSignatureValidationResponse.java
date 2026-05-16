package com.sealmail.app.dto.response;

import java.time.Instant;
import java.util.List;

public record SmimeSignatureValidationResponse(
        boolean valid,
        String signer,
        String signerEmail,
        Instant signingTime,
        String signatureAlgorithm,
        List<String> validationErrors,
        boolean certificateTrusted,
        boolean certificateRevoked,
        boolean certificateExpired,
        boolean trusted
) {
    public SmimeSignatureValidationResponse {
        validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
    }
}
