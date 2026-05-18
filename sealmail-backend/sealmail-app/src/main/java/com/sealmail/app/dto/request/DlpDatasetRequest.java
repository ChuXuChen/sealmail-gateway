package com.sealmail.app.dto.request;

public record DlpDatasetRequest(
        String name,
        String description,
        Boolean enabled
) {
}
