package com.sealmail.domain.dlp;

import com.sealmail.domain.policy.DispositionAction;

import java.time.Instant;
import java.util.List;

public record DlpRule(
        String id,
        String name,
        String description,
        DlpRuleType type,
        String pattern,
        String builtinCode,
        List<DlpContentKind> contentKinds,
        int minMatchCount,
        int maxEvidenceCount,
        DlpMaskingStrategy maskingStrategy,
        int priority,
        DispositionAction defaultAction,
        int severity,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public DlpRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("DLP rule id cannot be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("DLP rule name cannot be blank");
        }
        type = type != null ? type : DlpRuleType.REGEX;
        contentKinds = contentKinds == null ? List.of() : List.copyOf(contentKinds);
        minMatchCount = Math.max(1, minMatchCount);
        maxEvidenceCount = maxEvidenceCount <= 0 ? 5 : maxEvidenceCount;
        maskingStrategy = maskingStrategy != null ? maskingStrategy : DlpMaskingStrategy.DEFAULT;
        defaultAction = defaultAction != null ? defaultAction : DispositionAction.WARN;
        if (severity < 1 || severity > 10) {
            throw new IllegalArgumentException("DLP severity must be between 1 and 10");
        }
    }

    public boolean scansKind(DlpContentKind kind) {
        return contentKinds.isEmpty() || contentKinds.contains(kind);
    }
}
