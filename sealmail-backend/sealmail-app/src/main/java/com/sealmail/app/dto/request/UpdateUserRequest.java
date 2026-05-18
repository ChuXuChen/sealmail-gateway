package com.sealmail.app.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UpdateUserRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email,
        List<String> roles,
        List<String> managedDomains,
        Boolean active,
        Boolean locked
) {
    public UpdateUserRequest {
        roles = roles == null ? null : List.copyOf(roles);
        managedDomains = managedDomains == null ? null : List.copyOf(managedDomains);
    }
}
