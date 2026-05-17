package com.sealmail.domain.dlp;

import java.util.List;

public record DlpContentPart(
        String partId,
        DlpContentKind kind,
        String fileName,
        String contentType,
        long size,
        String text,
        boolean truncated,
        List<String> warnings
) {
    public DlpContentPart {
        if (partId == null || partId.isBlank()) {
            throw new IllegalArgumentException("DLP content part id cannot be blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("DLP content kind cannot be null");
        }
        text = text != null ? text : "";
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
