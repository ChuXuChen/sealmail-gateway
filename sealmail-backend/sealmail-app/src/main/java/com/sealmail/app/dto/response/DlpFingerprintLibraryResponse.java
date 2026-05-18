package com.sealmail.app.dto.response;

import java.time.Instant;

public record DlpFingerprintLibraryResponse(
        String id,
        String name,
        String description,
        boolean enabled,
        long documentCount,
        long chunkCount,
        Instant createdAt,
        Instant updatedAt
) {
}
