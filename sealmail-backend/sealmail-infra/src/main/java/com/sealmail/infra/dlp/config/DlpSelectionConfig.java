package com.sealmail.infra.dlp.config;

import com.sealmail.domain.dlp.DlpScopeType;

import java.time.Instant;
import java.util.List;

public record DlpSelectionConfig(
        String id,
        DlpScopeType scopeType,
        String scopeValue,
        List<String> patternIds,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
