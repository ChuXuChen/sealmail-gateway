package com.sealmail.app.dto.request;

public record DlpFingerprintImportRequest(
        String documentName,
        String text
) {
}
