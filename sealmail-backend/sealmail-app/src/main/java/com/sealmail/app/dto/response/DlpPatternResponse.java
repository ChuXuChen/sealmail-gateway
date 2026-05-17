package com.sealmail.app.dto.response;

import java.time.Instant;
import java.util.List;

public record DlpPatternResponse(
        String id,
        String name,
        String description,
        String regex,
        String type,
        String builtinCode,
        List<String> contentKinds,
        int minMatchCount,
        int maxEvidenceCount,
        String maskingStrategy,
        String action,
        int severity,
        int priority,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public DlpPatternResponse {
        contentKinds = contentKinds == null ? List.of() : List.copyOf(contentKinds);
    }
}
