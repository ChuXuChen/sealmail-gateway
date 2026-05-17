package com.sealmail.app.dto.request;

import java.util.List;

public record CreateDlpPatternRequest(
        String name,
        String description,
        String regex,
        String type,
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
    public CreateDlpPatternRequest {
        contentKinds = contentKinds == null ? null : List.copyOf(contentKinds);
    }
}
