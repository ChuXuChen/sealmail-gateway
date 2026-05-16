package com.sealmail.app.dto.request;

import java.util.List;

public record CreateDlpSelectionRequest(
        String scopeType,
        String scopeValue,
        List<String> patternIds,
        String patternMode,
        Boolean enabled
) {
    public CreateDlpSelectionRequest {
        patternIds = patternIds == null ? null : List.copyOf(patternIds);
    }
}
