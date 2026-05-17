package com.sealmail.domain.mailauth;

import java.util.Locale;

public enum AuthenticationResult {
    PASS,
    FAIL,
    SOFTFAIL,
    NEUTRAL,
    NONE,
    TEMPERROR,
    PERMERROR;

    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isFailure() {
        return this == FAIL || this == SOFTFAIL || this == TEMPERROR || this == PERMERROR;
    }
}
