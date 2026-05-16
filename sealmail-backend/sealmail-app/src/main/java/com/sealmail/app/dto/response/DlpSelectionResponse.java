package com.sealmail.app.dto.response;

import java.time.Instant;
import java.util.List;

public record DlpSelectionResponse(
        String id,
        String scopeType,
        String scopeValue,
        List<String> patternIds,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public DlpSelectionResponse {
        patternIds = patternIds == null ? null : List.copyOf(patternIds);
    }
}
