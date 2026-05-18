package com.sealmail.app.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserProfileUpdateRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email
) {
}
