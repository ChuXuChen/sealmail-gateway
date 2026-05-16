package com.sealmail.app.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserSummaryResponse {
    private String userId;
    private String username;
    private String email;
    private List<String> roles;
    private List<String> managedDomains;
    private boolean active;
    private boolean locked;
    private int failedLoginAttempts;
    private String lastLoginAt;
    private String lastPasswordChangedAt;
}
