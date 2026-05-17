package com.sealmail.domain.dlp;

import java.util.List;

public record DlpDetectorResult(
        String detectorName,
        List<DlpMatch> matches,
        long durationMs,
        List<String> warnings
) {
    public DlpDetectorResult {
        if (detectorName == null || detectorName.isBlank()) {
            throw new IllegalArgumentException("DLP detector name cannot be blank");
        }
        matches = matches == null ? List.of() : List.copyOf(matches);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
