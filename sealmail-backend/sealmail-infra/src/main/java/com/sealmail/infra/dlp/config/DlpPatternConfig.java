package com.sealmail.infra.dlp.config;

import com.sealmail.domain.dlp.DlpContentKind;
import com.sealmail.domain.dlp.DlpMaskingStrategy;
import com.sealmail.domain.dlp.DlpRuleType;
import com.sealmail.domain.policy.DispositionAction;

import java.time.Instant;
import java.util.List;

public record DlpPatternConfig(
        String id,
        String name,
        String description,
        String regex,
        DlpRuleType type,
        String builtinCode,
        List<DlpContentKind> contentKinds,
        int minMatchCount,
        int maxEvidenceCount,
        DlpMaskingStrategy maskingStrategy,
        DispositionAction action,
        int severity,
        int priority,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public DlpPatternConfig {
        type = type != null ? type : DlpRuleType.REGEX;
        contentKinds = contentKinds == null ? List.of() : List.copyOf(contentKinds);
        minMatchCount = Math.max(1, minMatchCount);
        maxEvidenceCount = maxEvidenceCount <= 0 ? 5 : maxEvidenceCount;
        maskingStrategy = maskingStrategy != null ? maskingStrategy : DlpMaskingStrategy.DEFAULT;
    }

    public DlpPatternConfig(String id,
                            String name,
                            String description,
                            String regex,
                            DispositionAction action,
                            int severity,
                            int priority,
                            boolean enabled,
                            Instant createdAt,
                            Instant updatedAt) {
        this(id, name, description, regex, DlpRuleType.REGEX, null, List.of(),
                1, 5, DlpMaskingStrategy.DEFAULT, action, severity, priority, enabled, createdAt, updatedAt);
    }
}
