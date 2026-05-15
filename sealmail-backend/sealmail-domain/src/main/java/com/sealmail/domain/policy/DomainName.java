package com.sealmail.domain.policy;

import java.util.Locale;
import java.util.regex.Pattern;

public final class DomainName {

    private static final int MAX_LENGTH = 253;
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,}$"
    );

    private DomainName() {
    }

    public static String normalize(String domain) {
        if (domain == null) {
            return null;
        }
        String normalized = domain.trim().toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public static String requireValid(String domain) {
        String normalized = normalize(domain);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("Domain cannot be blank");
        }
        if (normalized.length() > MAX_LENGTH || !DOMAIN_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid domain: " + domain);
        }
        return normalized;
    }
}
