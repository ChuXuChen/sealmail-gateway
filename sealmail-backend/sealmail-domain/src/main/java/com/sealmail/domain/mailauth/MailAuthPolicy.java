package com.sealmail.domain.mailauth;

import java.time.Instant;

public record MailAuthPolicy(
        String id,
        boolean enabled,
        String authservId,
        TrustedProxyMode trustedProxyMode,
        MailAuthFailureAction failureDefaultAction,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    public MailAuthPolicy {
        id = id != null && !id.isBlank() ? id.trim() : "default";
        authservId = authservId != null && !authservId.isBlank() ? authservId.trim() : "sealmail-gateway";
        trustedProxyMode = trustedProxyMode != null ? trustedProxyMode : TrustedProxyMode.DISABLED;
        failureDefaultAction = failureDefaultAction != null ? failureDefaultAction : MailAuthFailureAction.LOG_ONLY;
        Instant now = Instant.now();
        createdAt = createdAt != null ? createdAt : now;
        updatedAt = updatedAt != null ? updatedAt : createdAt;
    }

    public static MailAuthPolicy defaults() {
        return new MailAuthPolicy("default", true, "sealmail-gateway", TrustedProxyMode.DISABLED,
                MailAuthFailureAction.LOG_ONLY, null, null, 0);
    }
}
