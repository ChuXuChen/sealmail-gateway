package com.sealmail.app.dto.response;

import java.time.Instant;

public record DlpEvidenceResponse(
        String id,
        String eventId,
        String ruleId,
        String ruleName,
        String ruleType,
        String partId,
        String partKind,
        String fileName,
        String contentType,
        String maskedSnippet,
        String matchHash,
        int startOffset,
        int endOffset,
        int severity,
        String action,
        Instant createdAt
) {
}
