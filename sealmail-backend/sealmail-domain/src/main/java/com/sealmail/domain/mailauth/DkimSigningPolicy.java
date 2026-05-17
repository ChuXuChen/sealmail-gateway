package com.sealmail.domain.mailauth;

import java.util.List;
import java.util.Locale;

public record DkimSigningPolicy(
        boolean enabled,
        DkimSelector selector,
        DkimKeyRef keyRef,
        List<String> signedHeaders
) {

    private static final List<String> DEFAULT_SIGNED_HEADERS =
            List.of("from", "to", "subject", "date", "message-id");

    public DkimSigningPolicy {
        selector = selector != null ? selector : new DkimSelector("sealmail");
        keyRef = keyRef != null ? keyRef : DkimKeyRef.empty();
        signedHeaders = normalizeHeaders(signedHeaders);
    }

    public static DkimSigningPolicy disabled() {
        return new DkimSigningPolicy(false, new DkimSelector("sealmail"), DkimKeyRef.empty(), DEFAULT_SIGNED_HEADERS);
    }

    public boolean signingReady() {
        return enabled && keyRef.configured() && selector != null && !signedHeaders.isEmpty();
    }

    private static List<String> normalizeHeaders(List<String> headers) {
        List<String> normalized = headers == null || headers.isEmpty()
                ? DEFAULT_SIGNED_HEADERS
                : headers;
        return normalized.stream()
                .map(header -> header == null ? "" : header.trim().toLowerCase(Locale.ROOT))
                .filter(header -> !header.isBlank())
                .distinct()
                .toList();
    }
}
