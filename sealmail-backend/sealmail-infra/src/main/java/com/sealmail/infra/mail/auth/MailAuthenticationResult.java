package com.sealmail.infra.mail.auth;

public record MailAuthenticationResult(
        AuthResult spf,
        AuthResult dkim,
        AuthResult dmarc,
        DmarcPolicy dmarcPolicy,
        String header,
        String detail,
        boolean quarantineDmarcPolicyFailures
) {

    public MailAuthenticationResult(AuthResult spf,
                                    AuthResult dkim,
                                    AuthResult dmarc,
                                    DmarcPolicy dmarcPolicy,
                                    String header,
                                    String detail) {
        this(spf, dkim, dmarc, dmarcPolicy, header, detail, true);
    }

    public boolean shouldQuarantine() {
        return quarantineDmarcPolicyFailures
                && dmarc == AuthResult.FAIL
                && (dmarcPolicy == DmarcPolicy.QUARANTINE || dmarcPolicy == DmarcPolicy.REJECT);
    }
}
