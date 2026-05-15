package com.sealmail.infra.dlp.config;

public record DlpPatternUpdate(
        String name,
        String description,
        String regex,
        String action,
        Integer severity,
        Integer priority,
        Boolean enabled
) {
}
