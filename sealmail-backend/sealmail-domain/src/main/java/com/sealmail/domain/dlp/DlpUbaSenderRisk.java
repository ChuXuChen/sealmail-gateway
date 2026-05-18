package com.sealmail.domain.dlp;

import java.time.Instant;
import java.util.List;

public record DlpUbaSenderRisk(
        String senderEmail,
        long totalMessages,
        long outboundMessages,
        int externalDomainCount,
        long dlpHitCount,
        long highRiskCount,
        DlpUbaRiskLevel riskLevel,
        List<String> lastReasons,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Instant updatedAt
) {
    public DlpUbaSenderRisk {
        riskLevel = riskLevel != null ? riskLevel : DlpUbaRiskLevel.LOW;
        lastReasons = lastReasons == null ? List.of() : List.copyOf(lastReasons);
    }
}
