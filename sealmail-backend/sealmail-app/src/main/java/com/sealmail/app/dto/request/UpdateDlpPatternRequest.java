package com.sealmail.app.dto.request;

public record UpdateDlpPatternRequest(
        String name,
        String description,
        String regex,
        String action,
        Integer severity,
        Integer priority,
        Boolean enabled
) {
}
