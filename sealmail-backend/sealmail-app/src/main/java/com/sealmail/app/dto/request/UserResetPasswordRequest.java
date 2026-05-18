package com.sealmail.app.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UserResetPasswordRequest(
        @NotBlank(message = "新密码不能为空") String newPassword
) {
}
