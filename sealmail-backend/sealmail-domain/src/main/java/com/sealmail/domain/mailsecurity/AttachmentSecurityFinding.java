package com.sealmail.domain.mailsecurity;

import java.util.List;

public record AttachmentSecurityFinding(
        String code,
        int severity,
        AttachmentSecurityAction action,
        String message,
        String fileName,
        String extension,
        String declaredMimeType,
        String detectedMimeType,
        boolean archive,
        boolean encrypted,
        List<String> nestedPath
) {
    public AttachmentSecurityFinding {
        action = action != null ? action : AttachmentSecurityAction.WARN;
        message = message != null ? message : "";
        nestedPath = nestedPath != null ? List.copyOf(nestedPath) : List.of();
        severity = Math.max(0, severity);
    }
}
