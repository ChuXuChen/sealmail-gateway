package com.sealmail.domain.config;

import java.time.Instant;
import java.util.List;

public interface SmimeSuitePolicyPort {

    SmimeSuitePolicySettings getSettings();

    SmimeSuitePolicySettings updateSettings(SmimeSuitePolicySettingsUpdate update);

    record SmimeSuitePolicySettings(
            String defaultStandardSuite,
            String defaultGmSuite,
            List<SmimeSuiteOption> standardSuites,
            List<SmimeSuiteOption> gmSuites,
            Instant updatedAt
    ) {
        public SmimeSuitePolicySettings {
            standardSuites = standardSuites == null ? List.of() : List.copyOf(standardSuites);
            gmSuites = gmSuites == null ? List.of() : List.copyOf(gmSuites);
        }
    }

    record SmimeSuitePolicySettingsUpdate(
            String defaultStandardSuite,
            String defaultGmSuite
    ) {
    }

    record SmimeSuiteOption(
            String id,
            String displayName,
            String profile
    ) {
    }
}
