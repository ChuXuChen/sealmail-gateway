package com.sealmail.app.dto.response;

import java.time.Instant;
import java.util.List;

public record DlpUbaSenderRiskResponse(
        String senderEmail,
        long totalMessages,
        long outboundMessages,
        int externalDomainCount,
        long dlpHitCount,
        long highRiskCount,
        String riskLevel,
        List<String> lastReasons,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Instant updatedAt
) {
    public DlpUbaSenderRiskResponse {
        lastReasons = lastReasons == null ? List.of() : List.copyOf(lastReasons);
    }
}
