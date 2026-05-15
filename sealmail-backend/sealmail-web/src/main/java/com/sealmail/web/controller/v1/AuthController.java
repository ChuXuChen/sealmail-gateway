package com.sealmail.web.controller.v1;

import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.UserContext;
import com.sealmail.app.security.PasswordEncoder;
import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.audit.AuditService;
import com.sealmail.domain.user.UserAccount;
import com.sealmail.domain.user.UserAccountRepository;
import com.sealmail.web.security.JwtTokenProvider;
import com.sealmail.web.security.LocalAuthService;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户登录、登出、口令管理")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final LocalAuthService localAuthService;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名密码登录，获取JWT Token")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        LocalAuthService.AuthenticatedUser user =
                localAuthService.authenticate(request.getUsername(), request.getPassword(), clientIp(httpRequest));
        String token = jwtTokenProvider.createToken(
                user.userId(),
                user.username(),
                user.email(),
                user.roles(),
                user.managedDomains()
        );

        return ApiResponse.ok(LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn((int) jwtTokenProvider.getTokenValidityInSeconds())
                .user(UserInfo.builder()
                        .userId(user.userId())
                        .username(user.username())
                        .email(user.email())
                        .roles(user.roles())
                        .managedDomains(user.managedDomains())
                        .build())
                .build());
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "使当前用户已有Token失效")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UserContext user,
                                    HttpServletRequest httpRequest) {
        UserAccount userAccount = requireCurrentUser(user);
        userAccount.invalidateTokens();
        userAccountRepository.save(userAccount);
        auditService.recordUserAction(
                userAccount.getId(),
                userAccount.getUsername(),
                clientIp(httpRequest),
                AuditLogType.USER_LOGOUT.name(),
                "USER_ACCOUNT",
                userAccount.getId(),
                "用户登出并使现有令牌失效"
        );
        return ApiResponse.ok();
    }

    @PostMapping("/change-password")
    @Operation(summary = "修改当前用户密码", description = "修改密码后旧Token立即失效")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                            @AuthenticationPrincipal UserContext user,
                                            HttpServletRequest httpRequest) {
        UserAccount userAccount = requireCurrentUser(user);
        if (!passwordEncoder.matches(request.getCurrentPassword(), userAccount.getPasswordHash())) {
            throw BusinessException.badRequest("当前密码错误");
        }
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw BusinessException.badRequest("新密码不能与旧密码相同");
        }
        userAccount.changePasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userAccountRepository.save(userAccount);
        auditService.recordUserAction(
                userAccount.getId(),
                userAccount.getUsername(),
                clientIp(httpRequest),
                AuditLogType.USER_PASSWORD_CHANGED.name(),
                "USER_ACCOUNT",
                userAccount.getId(),
                "用户修改自身密码"
        );
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    @Operation(summary = "查询当前登录用户")
    public ApiResponse<UserInfo> me(@AuthenticationPrincipal UserContext user) {
        UserAccount userAccount = requireCurrentUser(user);
        return ApiResponse.ok(UserInfo.builder()
                .userId(userAccount.getId())
                .username(userAccount.getUsername())
                .email(userAccount.getEmail())
                .roles(userAccount.getRoles().stream().toList())
                .managedDomains(userAccount.getManagedDomains().stream().toList())
                .active(userAccount.isActive())
                .locked(userAccount.isLocked())
                .build());
    }

    private UserAccount requireCurrentUser(UserContext user) {
        if (user == null || user.getUserId() == null || user.getUserId().isBlank()) {
            throw BusinessException.unauthorized("未登录");
        }
        return userAccountRepository.findById(user.getUserId())
                .orElseThrow(() -> BusinessException.unauthorized("用户不存在"));
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

    @Data
    @Builder
    public static class LoginResponse {
        private String accessToken;
        private String tokenType;
        private int expiresIn;
        private UserInfo user;
    }

    @Data
    @Builder
    public static class UserInfo {
        private String userId;
        private String username;
        private String email;
        private List<String> roles;
        private List<String> managedDomains;
        private Boolean active;
        private Boolean locked;
    }
}
