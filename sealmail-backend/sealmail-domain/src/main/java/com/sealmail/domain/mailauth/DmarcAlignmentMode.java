package com.sealmail.domain.mailauth;

public enum DmarcAlignmentMode {
    RELAXED("r"),
    STRICT("s");

    private final String tagValue;

    DmarcAlignmentMode(String tagValue) {
        this.tagValue = tagValue;
    }

    public String tagValue() {
        return tagValue;
    }

    public static DmarcAlignmentMode fromTag(String value) {
        if ("s".equalsIgnoreCase(value)) {
            return STRICT;
        }
        return RELAXED;
    }
}
