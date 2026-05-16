package com.sealmail.app.dto.response;

import java.util.List;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserInfo user
) {
    public record UserInfo(
            String userId,
            String username,
            String email,
            List<String> roles,
            List<String> managedDomains,
            Boolean active,
            Boolean locked
    ) {
    }
}
