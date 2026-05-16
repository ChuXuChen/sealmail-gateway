package com.sealmail.app.dto.request;

public record CreateDlpPatternRequest(
        String name,
        String description,
        String regex,
        String action,
        Integer severity,
        Integer priority,
        Boolean enabled
) {
}
