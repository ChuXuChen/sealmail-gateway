package com.sealmail.app.dto.request;

import java.util.List;

public record DlpRuleRequest(
        String name,
        String description,
        String type,
        String pattern,
        String builtinCode,
        List<String> contentKinds,
        Integer minMatchCount,
        Integer maxEvidenceCount,
        String maskingStrategy,
        String action,
        Integer severity,
        Integer priority,
        Boolean enabled
) {
    public DlpRuleRequest {
        contentKinds = contentKinds == null ? null : List.copyOf(contentKinds);
    }
}
