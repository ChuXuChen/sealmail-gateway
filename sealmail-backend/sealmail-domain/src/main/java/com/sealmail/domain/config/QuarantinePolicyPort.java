package com.sealmail.domain.config;

import java.time.Instant;

public interface QuarantinePolicyPort {

    QuarantinePolicySettings getSettings();

    QuarantinePolicySettings updateSettings(QuarantinePolicySettingsUpdate update);

    record QuarantinePolicySettings(
            int maxRetentionDays,
            boolean notificationEnabled,
            boolean releaseRequiresEncryption,
            Instant updatedAt
    ) {
    }

    record QuarantinePolicySettingsUpdate(
            Integer maxRetentionDays,
            Boolean notificationEnabled,
            Boolean releaseRequiresEncryption
    ) {
    }
}
