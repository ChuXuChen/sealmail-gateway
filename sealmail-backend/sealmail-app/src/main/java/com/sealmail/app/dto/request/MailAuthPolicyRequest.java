package com.sealmail.app.dto.request;

public record MailAuthPolicyRequest(
        Boolean enabled,
        String authservId,
        String trustedProxyMode,
        String failureDefaultAction
) {
}
