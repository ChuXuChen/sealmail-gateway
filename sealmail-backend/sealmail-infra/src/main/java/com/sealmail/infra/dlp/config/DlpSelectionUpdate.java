package com.sealmail.infra.dlp.config;

import java.util.List;

public record DlpSelectionUpdate(
        String scopeType,
        String scopeValue,
        List<String> patternIds,
        String patternMode,
        Boolean enabled
) {
}
