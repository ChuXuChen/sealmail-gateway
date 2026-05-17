package com.sealmail.domain.dlp;

import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.policy.DispositionAction;

import java.time.Instant;
import java.util.List;

public record DlpScanEvent(
        String id,
        String messageId,
        String processingId,
        MailDirection direction,
        String senderEmail,
        List<String> recipients,
        String subject,
        String remoteAddress,
        List<String> policyIds,
        List<String> ruleGroupIds,
        DispositionAction action,
        int maxSeverity,
        int matchCount,
        List<String> extractionWarnings,
        boolean monitorMode,
        long scanDurationMs,
        String quarantineId,
        boolean falsePositive,
        Instant falsePositiveAt,
        String falsePositiveBy,
        String falsePositiveComment,
        Instant createdAt
) {
    public DlpScanEvent {
        recipients = recipients == null ? List.of() : List.copyOf(recipients);
        policyIds = policyIds == null ? List.of() : List.copyOf(policyIds);
        ruleGroupIds = ruleGroupIds == null ? List.of() : List.copyOf(ruleGroupIds);
        extractionWarnings = extractionWarnings == null ? List.of() : List.copyOf(extractionWarnings);
    }
}
