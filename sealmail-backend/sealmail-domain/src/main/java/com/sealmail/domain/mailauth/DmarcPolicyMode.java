package com.sealmail.domain.mailauth;

import java.util.Locale;

public enum DmarcPolicyMode {
    NONE,
    QUARANTINE,
    REJECT;

    public String tagValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DmarcPolicyMode fromTag(String value) {
        if ("reject".equalsIgnoreCase(value)) {
            return REJECT;
        }
        if ("quarantine".equalsIgnoreCase(value)) {
            return QUARANTINE;
        }
        return NONE;
    }
}
