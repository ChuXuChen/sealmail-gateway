package com.sealmail.app.dto.response;

import java.time.Instant;
import java.util.List;

public record SmimeSuitePolicyResponse(
        String defaultStandardSuite,
        String defaultGmSuite,
        List<SmimeSuiteOptionResponse> standardSuites,
        List<SmimeSuiteOptionResponse> gmSuites,
        Instant updatedAt
) {
    public SmimeSuitePolicyResponse {
        standardSuites = standardSuites == null ? List.of() : List.copyOf(standardSuites);
        gmSuites = gmSuites == null ? List.of() : List.copyOf(gmSuites);
    }

    public record SmimeSuiteOptionResponse(
            String id,
            String displayName,
            String profile
    ) {
    }
}
