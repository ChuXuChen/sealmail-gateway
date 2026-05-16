package com.sealmail.app.dto.response;

import java.time.Instant;

public record DlpPatternResponse(
        String id,
        String name,
        String description,
        String regex,
        String action,
        int severity,
        int priority,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
