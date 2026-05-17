package com.sealmail.domain.mailauth;

public record DkimKeyRef(
        String secretRef,
        String path
) {

    public DkimKeyRef {
        secretRef = blankToNull(secretRef);
        path = blankToNull(path);
        if (secretRef != null && path != null) {
            throw new IllegalArgumentException("DKIM key must use either secret ref or path, not both");
        }
    }

    public static DkimKeyRef empty() {
        return new DkimKeyRef(null, null);
    }

    public boolean configured() {
        return secretRef != null || path != null;
    }

    public String displayRef() {
        if (secretRef != null) {
            return secretRef;
        }
        return path;
    }

    private static String blankToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
