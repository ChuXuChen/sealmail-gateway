package com.sealmail.domain.mailsecurity;

import java.util.List;

public record AttachmentDescriptor(
        String partId,
        String fileName,
        String declaredMimeType,
        String detectedMimeType,
        String extension,
        long size,
        String sha256,
        boolean archive,
        boolean encrypted,
        boolean truncated,
        Long archiveEntryCount,
        Long archiveExpandedBytes,
        List<String> nestedPath,
        List<String> warnings
) {
    public AttachmentDescriptor {
        nestedPath = nestedPath != null ? List.copyOf(nestedPath) : List.of();
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }
}
