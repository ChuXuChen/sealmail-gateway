package com.sealmail.app.dto.request;

import java.util.List;

public record DlpImportValuesRequest(
        List<String> values,
        String text
) {
    public DlpImportValuesRequest {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
