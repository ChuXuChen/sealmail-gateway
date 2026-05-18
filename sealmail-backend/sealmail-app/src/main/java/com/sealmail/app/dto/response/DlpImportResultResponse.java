package com.sealmail.app.dto.response;

public record DlpImportResultResponse(
        long importedCount,
        long duplicateCount,
        long ignoredCount,
        long totalCount
) {
}
