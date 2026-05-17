package com.sealmail.domain.dlp;

import com.sealmail.domain.policy.DispositionAction;

import java.util.List;

public record DlpEvaluationResult(
        String eventId,
        DispositionAction action,
        DispositionAction recommendedAction,
        int maxSeverity,
        List<DlpMatch> matches,
        List<DlpEvidence> evidence,
        List<String> warnings,
        List<String> policyIds,
        List<String> ruleGroupIds,
        boolean monitorMode,
        long scanDurationMs
) {
    public DlpEvaluationResult {
        action = action != null ? action : DispositionAction.WARN;
        recommendedAction = recommendedAction != null ? recommendedAction : action;
        matches = matches == null ? List.of() : List.copyOf(matches);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        policyIds = policyIds == null ? List.of() : List.copyOf(policyIds);
        ruleGroupIds = ruleGroupIds == null ? List.of() : List.copyOf(ruleGroupIds);
        maxSeverity = Math.max(0, maxSeverity);
    }

    public boolean hasMatches() {
        return !matches.isEmpty();
    }
}
