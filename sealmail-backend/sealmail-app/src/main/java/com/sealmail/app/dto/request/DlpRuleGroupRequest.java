package com.sealmail.app.dto.request;

import java.util.List;

public record DlpRuleGroupRequest(
        String name,
        String description,
        Boolean enabled,
        Integer priority,
        List<String> ruleIds
) {
    public DlpRuleGroupRequest {
        ruleIds = ruleIds == null ? null : List.copyOf(ruleIds);
    }
}
