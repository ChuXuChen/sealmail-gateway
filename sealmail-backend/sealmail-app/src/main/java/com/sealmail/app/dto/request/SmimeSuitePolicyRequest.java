package com.sealmail.app.dto.request;

public record SmimeSuitePolicyRequest(
        String defaultStandardSuite,
        String defaultGmSuite
) {
}
