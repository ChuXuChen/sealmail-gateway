package com.sealmail.app.dto.response;

import java.util.List;

public record DlpEvaluationResponse(
        String eventId,
        String action,
        String recommendedAction,
        int maxSeverity,
        int matchCount,
        List<String> policyIds,
        List<String> ruleGroupIds,
        boolean monitorMode,
        long scanDurationMs,
        List<String> warnings,
        List<DlpEvidenceResponse> evidence,
        String ubaRiskLevel,
        List<String> ubaRiskReasons,
        boolean ubaActionUpgraded
) {
    public DlpEvaluationResponse {
        policyIds = policyIds == null ? List.of() : List.copyOf(policyIds);
        ruleGroupIds = ruleGroupIds == null ? List.of() : List.copyOf(ruleGroupIds);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        ubaRiskReasons = ubaRiskReasons == null ? List.of() : List.copyOf(ubaRiskReasons);
    }
}
