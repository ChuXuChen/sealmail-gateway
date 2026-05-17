package com.sealmail.domain.mailauth;

public record AuthenticationMechanismResult(
        AuthenticationMechanism mechanism,
        AuthenticationResult result,
        String domain,
        String identity,
        String detail
) {

    public AuthenticationMechanismResult {
        if (mechanism == null) {
            throw new IllegalArgumentException("Authentication mechanism cannot be null");
        }
        result = result != null ? result : AuthenticationResult.NONE;
    }

    public static AuthenticationMechanismResult none(AuthenticationMechanism mechanism, String detail) {
        return new AuthenticationMechanismResult(mechanism, AuthenticationResult.NONE, null, null, detail);
    }
}
