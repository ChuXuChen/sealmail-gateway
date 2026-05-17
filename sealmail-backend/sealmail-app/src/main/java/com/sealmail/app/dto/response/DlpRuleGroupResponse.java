package com.sealmail.app.dto.response;

import java.time.Instant;
import java.util.List;

public record DlpRuleGroupResponse(
        String id,
        String name,
        String description,
        boolean enabled,
        int priority,
        List<String> ruleIds,
        Instant createdAt,
        Instant updatedAt
) {
    public DlpRuleGroupResponse {
        ruleIds = ruleIds == null ? List.of() : List.copyOf(ruleIds);
    }
}
