package com.sealmail.domain.mailauth;

public record DkimSelector(String value) {

    public DkimSelector {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DKIM selector cannot be blank");
        }
        value = value.trim();
        if (!value.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException("Invalid DKIM selector: " + value);
        }
    }
}
