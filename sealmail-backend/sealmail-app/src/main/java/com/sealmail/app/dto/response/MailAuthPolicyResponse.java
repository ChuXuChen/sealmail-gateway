package com.sealmail.app.dto.response;

import com.sealmail.domain.mailauth.MailAuthPolicy;

import java.time.Instant;

public record MailAuthPolicyResponse(
        String id,
        boolean enabled,
        String authservId,
        String trustedProxyMode,
        String failureDefaultAction,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    public static MailAuthPolicyResponse from(MailAuthPolicy policy) {
        return new MailAuthPolicyResponse(
                policy.id(),
                policy.enabled(),
                policy.authservId(),
                policy.trustedProxyMode().name(),
                policy.failureDefaultAction().name(),
                policy.createdAt(),
                policy.updatedAt(),
                policy.version());
    }
}
