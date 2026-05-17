package com.sealmail.domain.dlp;

import com.sealmail.domain.mailsecurity.MailDirection;

import java.time.Instant;
import java.util.List;

public record DlpPolicy(
        String id,
        String name,
        String description,
        DlpPolicyMode mode,
        MailDirection direction,
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
    public DlpPolicy {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("DLP policy id cannot be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("DLP policy name cannot be blank");
        }
        mode = mode != null ? mode : DlpPolicyMode.ENFORCE;
        senderDomains = senderDomains == null ? List.of() : List.copyOf(senderDomains);
        recipientDomains = recipientDomains == null ? List.of() : List.copyOf(recipientDomains);
        senderAddressPatterns = senderAddressPatterns == null ? List.of() : List.copyOf(senderAddressPatterns);
        recipientAddressPatterns = recipientAddressPatterns == null ? List.of() : List.copyOf(recipientAddressPatterns);
        ruleGroupIds = ruleGroupIds == null ? List.of() : List.copyOf(ruleGroupIds);
    }
}
