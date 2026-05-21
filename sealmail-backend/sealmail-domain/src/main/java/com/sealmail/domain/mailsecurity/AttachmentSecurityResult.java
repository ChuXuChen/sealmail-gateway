package com.sealmail.domain.mailsecurity;

import java.util.List;

public record AttachmentSecurityResult(
        String status,
        AttachmentSecurityAction action,
        int maxSeverity,
        int attachmentCount,
        long totalBytes,
        List<AttachmentSecurityFinding> findings,
        List<String> warnings
) {
    public AttachmentSecurityResult {
        status = status != null ? status : MailProcessingStatusSnapshot.PENDING;
        action = action != null ? action : AttachmentSecurityAction.ALLOW;
        findings = findings != null ? List.copyOf(findings) : List.of();
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
        maxSeverity = Math.max(0, maxSeverity);
        attachmentCount = Math.max(0, attachmentCount);
        totalBytes = Math.max(0L, totalBytes);
    }

    public boolean hasFindings() {
        return !findings.isEmpty();
    }
}
