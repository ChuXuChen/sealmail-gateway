package com.sealmail.domain.mailauth;

public enum MailAuthFailureAction {
    LOG_ONLY,
    APPLY_POLICY,
    FORCE_QUARANTINE,
    FORCE_ALLOW
}
