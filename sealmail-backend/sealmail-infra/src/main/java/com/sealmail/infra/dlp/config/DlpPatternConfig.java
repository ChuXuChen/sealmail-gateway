package com.sealmail.infra.dlp.config;

import com.sealmail.domain.policy.DispositionAction;

import java.time.Instant;

public record DlpPatternConfig(
        String id,
        String name,
        String description,
        String regex,
        DispositionAction action,
        int severity,
        int priority,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
