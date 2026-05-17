package com.sealmail.domain.mailsecurity;

import java.util.Locale;

public enum SmtpTransportSecurity {
    NONE,
    STARTTLS,
    SMTPS;

    public boolean usesTls() {
        return this != NONE;
    }

    public boolean usesStartTls() {
        return this == STARTTLS;
    }

    public boolean usesImplicitTls() {
        return this == SMTPS;
    }

    public static SmtpTransportSecurity fromLegacyUseTls(boolean useTls, int port) {
        if (!useTls) {
            return NONE;
        }
        return port == 465 ? SMTPS : STARTTLS;
    }

    public static SmtpTransportSecurity fromNullable(String value, SmtpTransportSecurity fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
