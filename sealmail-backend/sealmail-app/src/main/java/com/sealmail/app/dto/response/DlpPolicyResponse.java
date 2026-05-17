package com.sealmail.app.dto.response;

import java.time.Instant;
import java.util.List;

public record DlpPolicyResponse(
        String id,
        String name,
        String description,
        String mode,
        String direction,
        List<String> senderDomains,
        List<String> recipientDomains,
        List<String> senderAddressPatterns,
        List<String> recipientAddressPatterns,
        boolean attachmentRequired,
        boolean enabled,
        int priority,
        List<String> ruleGroupIds,
        Instant createdAt,
        Instant updatedAt
) {
    public DlpPolicyResponse {
        senderDomains = senderDomains == null ? List.of() : List.copyOf(senderDomains);
        recipientDomains = recipientDomains == null ? List.of() : List.copyOf(recipientDomains);
        senderAddressPatterns = senderAddressPatterns == null ? List.of() : List.copyOf(senderAddressPatterns);
        recipientAddressPatterns = recipientAddressPatterns == null ? List.of() : List.copyOf(recipientAddressPatterns);
        ruleGroupIds = ruleGroupIds == null ? List.of() : List.copyOf(ruleGroupIds);
    }
}
