package com.sealmail.domain.mailauth;

public record AuthenticationResultsHeader(
        String authservId,
        String value
) {

    public AuthenticationResultsHeader {
        if (authservId == null || authservId.isBlank()) {
            throw new IllegalArgumentException("Authentication service id cannot be blank");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Authentication-Results value cannot be blank");
        }
    }
}
