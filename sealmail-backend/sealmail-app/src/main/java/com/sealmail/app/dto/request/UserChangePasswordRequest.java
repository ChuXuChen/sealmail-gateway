package com.sealmail.app.dto.request;

public record UserChangePasswordRequest(
        String currentPassword,
        String newPassword
) {
}
