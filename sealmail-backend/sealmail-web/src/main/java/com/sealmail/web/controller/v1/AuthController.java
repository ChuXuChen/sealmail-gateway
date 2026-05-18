package com.sealmail.web.controller.v1;

import com.sealmail.app.dto.request.UserChangePasswordRequest;
import com.sealmail.app.dto.request.UserProfileUpdateRequest;
import com.sealmail.app.dto.response.LoginResponse;
import com.sealmail.app.dto.response.UserSummaryResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.usecase.auth.LoginUseCase;
import com.sealmail.app.usecase.auth.UserAccountUseCase;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户登录、登出、口令管理")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final UserAccountUseCase userAccountUseCase;

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名密码登录，获取访问令牌")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        return ApiResponse.ok(loginUseCase.execute(request.getUsername(), request.getPassword(), clientIp(httpRequest)));
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "使当前用户已有Token失效")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UserContext user,
                                    HttpServletRequest httpRequest) {
        userAccountUseCase.logout(user, clientIp(httpRequest));
        return ApiResponse.ok();
    }

    @PostMapping("/change-password")
    @Operation(summary = "修改当前用户密码", description = "修改密码后旧Token立即失效")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                            @AuthenticationPrincipal UserContext user,
                                            HttpServletRequest httpRequest) {
        userAccountUseCase.changePassword(
                new UserChangePasswordRequest(request.getCurrentPassword(), request.getNewPassword()),
                user,
                clientIp(httpRequest));
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    @Operation(summary = "查询当前登录用户")
    public ApiResponse<LoginResponse.UserInfo> me(@AuthenticationPrincipal UserContext user) {
        UserSummaryResponse userAccount = userAccountUseCase.currentUser(user);
        return ApiResponse.ok(new LoginResponse.UserInfo(
                userAccount.getUserId(),
                userAccount.getUsername(),
                userAccount.getEmail(),
                userAccount.getRoles(),
                userAccount.getManagedDomains(),
                userAccount.isActive(),
                userAccount.isLocked()));
    }

    @PutMapping("/profile")
    @Operation(summary = "修改当前用户资料", description = "修改用户名或邮箱后旧Token立即失效")
    public ApiResponse<LoginResponse.UserInfo> updateProfile(@Valid @RequestBody UserProfileUpdateRequest request,
                                                             @AuthenticationPrincipal UserContext user,
                                                             HttpServletRequest httpRequest) {
        UserSummaryResponse userAccount = userAccountUseCase.updateProfile(request, user, clientIp(httpRequest));
        return ApiResponse.ok(new LoginResponse.UserInfo(
                userAccount.getUserId(),
                userAccount.getUsername(),
                userAccount.getEmail(),
                userAccount.getRoles(),
                userAccount.getManagedDomains(),
                userAccount.isActive(),
                userAccount.isLocked()));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank(message = "当前密码不能为空")
        private String currentPassword;

        @NotBlank(message = "新密码不能为空")
        private String newPassword;
    }

}
