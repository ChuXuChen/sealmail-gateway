package com.sealmail.domain.mailauth;

public record MailAuthDecision(
        MailAuthFailureAction action,
        String reason,
        String detail
) {

    public MailAuthDecision {
        action = action != null ? action : MailAuthFailureAction.LOG_ONLY;
    }

    public static MailAuthDecision recordOnly(String detail) {
        return new MailAuthDecision(MailAuthFailureAction.LOG_ONLY, "MAIL_AUTH_RECORD_ONLY", detail);
    }

    public static MailAuthDecision applyPolicy(String detail) {
        return new MailAuthDecision(MailAuthFailureAction.APPLY_POLICY, "EMAIL_AUTH_FAILED", detail);
    }

    public static MailAuthDecision forceQuarantine(String detail) {
        return new MailAuthDecision(MailAuthFailureAction.FORCE_QUARANTINE, "EMAIL_AUTH_FAILED", detail);
    }

    public boolean requiresQuarantine() {
        return action == MailAuthFailureAction.APPLY_POLICY || action == MailAuthFailureAction.FORCE_QUARANTINE;
    }
}
