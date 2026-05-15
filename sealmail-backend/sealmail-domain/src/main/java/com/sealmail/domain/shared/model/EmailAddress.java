package com.sealmail.domain.shared.model;

import java.util.Objects;
import java.util.regex.Pattern;

public final class EmailAddress extends ValueObject {

    /**
     * RFC 5322 simplified: supports dot-atom local-part (including + tagging),
     * and domain with dot-separated labels. Quoted strings and comments are
     * intentionally excluded — they are valid per RFC but rarely used in
     * gateway scenarios and pose injection risks.
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
                    + "@"
                    + "(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\\.)+"
                    + "[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?$"
    );

    private static final int MAX_LENGTH = 254; // RFC 5321

    private final String value;

    public EmailAddress(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Email address cannot be null or blank");
        }
        String normalized = value.trim().toLowerCase();
        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Email address exceeds maximum length of " + MAX_LENGTH);
        }
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid email address: " + value);
        }
        this.value = normalized;
    }

    public String getValue() {
        return value;
    }

    public String getDomain() {
        return value.substring(value.indexOf('@') + 1);
    }

    public String getLocalPart() {
        return value.substring(0, value.indexOf('@'));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmailAddress that = (EmailAddress) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
