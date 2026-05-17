package com.sealmail.app.dto.request;

import java.util.List;

public record RotateDkimSelectorRequest(
        String selector,
        String keySecretRef,
        String keyPath,
        List<String> signedHeaders
) {
    public RotateDkimSelectorRequest {
        signedHeaders = signedHeaders == null ? null : List.copyOf(signedHeaders);
    }
}
