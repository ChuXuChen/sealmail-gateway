package com.sealmail.app.dto.request;

import java.util.List;

public record DlpPolicyRequest(
        String name,
        String description,
        String mode,
        String direction,
        List<String> senderDomains,
        List<String> recipientDomains,
        List<String> senderAddressPatterns,
        List<String> recipientAddressPatterns,
        Boolean attachmentRequired,
        Boolean enabled,
        Integer priority,
        List<String> ruleGroupIds
) {
    public DlpPolicyRequest {
        senderDomains = senderDomains == null ? null : List.copyOf(senderDomains);
        recipientDomains = recipientDomains == null ? null : List.copyOf(recipientDomains);
        senderAddressPatterns = senderAddressPatterns == null ? null : List.copyOf(senderAddressPatterns);
        recipientAddressPatterns = recipientAddressPatterns == null ? null : List.copyOf(recipientAddressPatterns);
        ruleGroupIds = ruleGroupIds == null ? null : List.copyOf(ruleGroupIds);
    }
}
