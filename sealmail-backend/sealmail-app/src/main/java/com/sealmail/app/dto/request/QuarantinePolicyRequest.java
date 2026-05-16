package com.sealmail.app.dto.request;

public record QuarantinePolicyRequest(
        Integer maxRetentionDays,
        Boolean notificationEnabled,
        Boolean releaseRequiresEncryption
) {
}
