package com.sealmail.infra.dlp.config;

public record DlpPatternUpdate(
        String name,
        String description,
        String regex,
        String type,
        String builtinCode,
        java.util.List<String> contentKinds,
        Integer minMatchCount,
        Integer maxEvidenceCount,
        String maskingStrategy,
        String action,
        Integer severity,
        Integer priority,
        Boolean enabled
) {
    public DlpPatternUpdate {
        contentKinds = contentKinds == null ? null : java.util.List.copyOf(contentKinds);
    }

    public DlpPatternUpdate(String name,
                            String description,
                            String regex,
                            String action,
                            Integer severity,
                            Integer priority,
                            Boolean enabled) {
        this(name, description, regex, null, null, null, null, null, null,
                action, severity, priority, enabled);
    }
}
