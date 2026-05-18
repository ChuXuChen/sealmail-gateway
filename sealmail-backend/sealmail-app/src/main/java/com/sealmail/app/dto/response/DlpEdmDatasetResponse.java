package com.sealmail.app.dto.response;

import java.time.Instant;

public record DlpEdmDatasetResponse(
        String id,
        String name,
        String description,
        boolean enabled,
        long valueCount,
        Instant createdAt,
        Instant updatedAt
) {
}
