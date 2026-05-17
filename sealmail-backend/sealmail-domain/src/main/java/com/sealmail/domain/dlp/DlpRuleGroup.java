package com.sealmail.domain.dlp;

import java.time.Instant;
import java.util.List;

public record DlpRuleGroup(
        String id,
        String name,
        String description,
        boolean enabled,
        int priority,
        List<String> ruleIds,
        Instant createdAt,
        Instant updatedAt
) {
    public DlpRuleGroup {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("DLP rule group id cannot be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("DLP rule group name cannot be blank");
        }
        ruleIds = ruleIds == null ? List.of() : List.copyOf(ruleIds);
    }
}
