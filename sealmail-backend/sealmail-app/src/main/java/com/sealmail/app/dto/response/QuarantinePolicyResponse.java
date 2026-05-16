package com.sealmail.app.dto.response;

import java.time.Instant;

public record QuarantinePolicyResponse(
        int maxRetentionDays,
        boolean notificationEnabled,
        boolean releaseRequiresEncryption,
        Instant updatedAt
) {
}
