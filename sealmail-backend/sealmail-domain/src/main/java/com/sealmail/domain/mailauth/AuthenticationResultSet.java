package com.sealmail.domain.mailauth;

import java.util.List;

public record AuthenticationResultSet(
        AuthenticationMechanismResult spf,
        List<AuthenticationMechanismResult> dkim,
        AuthenticationMechanismResult dmarc,
        MailAuthDecision decision,
        AuthenticationResultsHeader authenticationResultsHeader
) {

    public AuthenticationResultSet {
        spf = spf != null ? spf : AuthenticationMechanismResult.none(AuthenticationMechanism.SPF, null);
        dkim = dkim != null ? List.copyOf(dkim) : List.of();
        dmarc = dmarc != null ? dmarc : AuthenticationMechanismResult.none(AuthenticationMechanism.DMARC, null);
        decision = decision != null ? decision : MailAuthDecision.recordOnly("No mail authentication decision");
    }

    public static AuthenticationResultSet empty() {
        return new AuthenticationResultSet(
                AuthenticationMechanismResult.none(AuthenticationMechanism.SPF, null),
                List.of(),
                AuthenticationMechanismResult.none(AuthenticationMechanism.DMARC, null),
                MailAuthDecision.recordOnly("Mail authentication not evaluated"),
                null);
    }

    public AuthenticationMechanismResult bestDkim() {
        return dkim.stream()
                .filter(result -> result.result() == AuthenticationResult.PASS)
                .findFirst()
                .orElseGet(() -> dkim.stream().findFirst()
                        .orElse(AuthenticationMechanismResult.none(AuthenticationMechanism.DKIM, null)));
    }
}
