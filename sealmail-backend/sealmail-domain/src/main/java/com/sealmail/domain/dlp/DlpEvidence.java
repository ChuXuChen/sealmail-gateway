package com.sealmail.domain.dlp;

import com.sealmail.domain.policy.DispositionAction;

import java.time.Instant;

public record DlpEvidence(
        String id,
        String eventId,
        String ruleId,
        String ruleName,
        DlpRuleType ruleType,
        String partId,
        DlpContentKind partKind,
        String fileName,
        String contentType,
        String maskedSnippet,
        String matchHash,
        int startOffset,
        int endOffset,
        int severity,
        DispositionAction action,
        Instant createdAt
) {
}
