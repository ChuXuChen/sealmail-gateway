package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.policy.DispositionAction;

import java.util.List;

public record DlpDecision(
        DispositionAction action,
        int maxSeverity,
        List<String> ruleNames
) {

    public DlpDecision {
        action = action != null ? action : DispositionAction.WARN;
        if (maxSeverity < 0) {
            throw new IllegalArgumentException("DLP severity cannot be negative");
        }
        ruleNames = ruleNames == null ? List.of() : List.copyOf(ruleNames);
    }

    public static DlpDecision none() {
        return new DlpDecision(DispositionAction.WARN, 0, List.of());
    }

    public boolean requiresEncryption() {
        return action == DispositionAction.MUST_ENCRYPT;
    }

    public boolean requiresQuarantine() {
        return action == DispositionAction.BLOCK || action == DispositionAction.QUARANTINE;
    }
}
