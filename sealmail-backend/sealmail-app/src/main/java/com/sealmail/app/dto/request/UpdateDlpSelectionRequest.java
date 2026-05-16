package com.sealmail.app.dto.request;

import java.util.List;

public record UpdateDlpSelectionRequest(
        String scopeType,
        String scopeValue,
        List<String> patternIds,
        String patternMode,
        Boolean enabled
) {
    public UpdateDlpSelectionRequest {
        patternIds = patternIds == null ? null : List.copyOf(patternIds);
    }
}
