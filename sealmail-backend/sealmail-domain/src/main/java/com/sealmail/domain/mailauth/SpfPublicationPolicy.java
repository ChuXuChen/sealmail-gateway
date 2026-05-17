package com.sealmail.domain.mailauth;

import java.util.ArrayList;
import java.util.List;

public record SpfPublicationPolicy(
        boolean enabled,
        boolean useA,
        boolean useMx,
        List<String> ip4,
        List<String> ip6,
        List<String> includes,
        String allPolicy
) {

    public SpfPublicationPolicy {
        ip4 = ip4 != null ? List.copyOf(ip4) : List.of();
        ip6 = ip6 != null ? List.copyOf(ip6) : List.of();
        includes = includes != null ? List.copyOf(includes) : List.of();
        allPolicy = normalizeAllPolicy(allPolicy);
    }

    public static SpfPublicationPolicy disabled() {
        return new SpfPublicationPolicy(false, false, false, List.of(), List.of(), List.of(), "~all");
    }

    public String txtValue() {
        List<String> parts = new ArrayList<>();
        parts.add("v=spf1");
        if (useA) {
            parts.add("a");
        }
        if (useMx) {
            parts.add("mx");
        }
        ip4.forEach(value -> parts.add("ip4:" + value));
        ip6.forEach(value -> parts.add("ip6:" + value));
        includes.forEach(value -> parts.add("include:" + value));
        parts.add(allPolicy);
        return String.join(" ", parts);
    }

    private static String normalizeAllPolicy(String value) {
        if (value == null || value.isBlank()) {
            return "~all";
        }
        String normalized = value.trim().toLowerCase();
        if (!normalized.matches("[+\\-~?]?all")) {
            throw new IllegalArgumentException("Invalid SPF all policy: " + value);
        }
        return normalized.charAt(0) == 'a' ? "+" + normalized : normalized;
    }
}
