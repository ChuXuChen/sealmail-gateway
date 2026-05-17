package com.sealmail.app.dto.request;

import java.util.List;

public record UpdateDlpPatternRequest(
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
    public UpdateDlpPatternRequest {
        contentKinds = contentKinds == null ? null : List.copyOf(contentKinds);
    }
}
