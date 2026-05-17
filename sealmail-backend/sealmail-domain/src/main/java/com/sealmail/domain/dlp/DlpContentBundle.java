package com.sealmail.domain.dlp;

import java.util.List;
import java.util.stream.Stream;

public record DlpContentBundle(
        List<DlpContentPart> parts,
        List<String> warnings
) {
    public DlpContentBundle {
        parts = parts == null ? List.of() : List.copyOf(parts);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public List<String> allWarnings() {
        return Stream.concat(
                        warnings.stream(),
                        parts.stream().flatMap(part -> part.warnings().stream()))
                .distinct()
                .toList();
    }

    public boolean hasAttachments() {
        return parts.stream().anyMatch(part -> switch (part.kind()) {
            case ATTACHMENT_TEXT, ATTACHMENT_PDF, ATTACHMENT_ZIP_ENTRY, ATTACHMENT_METADATA -> true;
            default -> false;
        });
    }
}
