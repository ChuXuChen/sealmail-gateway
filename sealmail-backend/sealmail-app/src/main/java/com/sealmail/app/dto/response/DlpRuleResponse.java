package com.sealmail.app.dto.response;

import java.time.Instant;
import java.util.List;

public record DlpRuleResponse(
        String id,
        String name,
        String description,
        String type,
        String pattern,
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
    public DlpRuleResponse {
        contentKinds = contentKinds == null ? List.of() : List.copyOf(contentKinds);
    }
}
