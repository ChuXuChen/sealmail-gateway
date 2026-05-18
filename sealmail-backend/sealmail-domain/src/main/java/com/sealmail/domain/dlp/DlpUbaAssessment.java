package com.sealmail.domain.dlp;

import com.sealmail.domain.policy.DispositionAction;

import java.util.List;

public record DlpUbaAssessment(
        DlpUbaRiskLevel riskLevel,
        List<String> reasons,
        boolean actionUpgraded,
        DispositionAction upgradedAction
) {
    public DlpUbaAssessment {
        riskLevel = riskLevel != null ? riskLevel : DlpUbaRiskLevel.LOW;
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static DlpUbaAssessment low(DispositionAction action) {
        return new DlpUbaAssessment(DlpUbaRiskLevel.LOW, List.of(), false, action);
    }
}
