package com.sealmail.web.controller.v1;

import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.audit.AuditService;
import com.sealmail.domain.user.UserAccount;
import com.sealmail.domain.user.UserAccountRepository;
import com.sealmail.web.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "超级管理员用户管理")
public class UserAdminController {

    private final UserAccountRepository userAccountRepository;
    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "查询用户列表")
    public ApiResponse<List<UserSummary>> list(@AuthenticationPrincipal UserContext user) {
        requireSuperAdmin(user);
        return ApiResponse.ok(userAccountRepository.findAll().stream()
                .map(this::toSummary)
                .toList());
    }

    @PostMapping("/{userId}/disable")
    @Operation(summary = "禁用用户")
    public ApiResponse<UserSummary> disable(@PathVariable String userId,
                                            @AuthenticationPrincipal UserContext user,
                                            HttpServletRequest request) {
        requireSuperAdmin(user);
        UserAccount target = loadUser(userId);
        target.disable();
        target.invalidateTokens();
        userAccountRepository.save(target);
        recordUserUpdate(user, request, target, "禁用用户");
        return ApiResponse.ok(toSummary(target));
    }

    @PostMapping("/{userId}/enable")
    @Operation(summary = "启用用户")
    public ApiResponse<UserSummary> enable(@PathVariable String userId,
                                           @AuthenticationPrincipal UserContext user,
                                           HttpServletRequest request) {
        requireSuperAdmin(user);
        UserAccount target = loadUser(userId);
        target.enable();
        target.invalidateTokens();
        userAccountRepository.save(target);
        recordUserUpdate(user, request, target, "启用用户");
        return ApiResponse.ok(toSummary(target));
    }

    @PostMapping("/{userId}/unlock")
    @Operation(summary = "解锁用户")
    public ApiResponse<UserSummary> unlock(@PathVariable String userId,
                                           @AuthenticationPrincipal UserContext user,
                                           HttpServletRequest request) {
        requireSuperAdmin(user);
        UserAccount target = loadUser(userId);
        target.unlock();
        target.invalidateTokens();
        userAccountRepository.save(target);
        auditService.recordUserAction(
                user.getUserId(),
                user.getUsername(),
                clientIp(request),
                AuditLogType.USER_UNLOCKED.name(),
                "USER_ACCOUNT",
                target.getId(),
                "解锁用户: " + target.getUsername()
        );
        return ApiResponse.ok(toSummary(target));
    }

    private void requireSuperAdmin(UserContext user) {
        if (user == null || !user.hasRole("SUPER_ADMIN")) {
            throw BusinessException.forbidden("只有超级管理员可以执行此操作");
        }
    }

    private UserAccount loadUser(String userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> BusinessException.badRequest("用户不存在"));
    }

    private void recordUserUpdate(UserContext operator,
                                  HttpServletRequest request,
                                  UserAccount target,
                                  String detail) {
        auditService.recordUserAction(
                operator.getUserId(),
                operator.getUsername(),
                clientIp(request),
                AuditLogType.USER_UPDATED.name(),
                "USER_ACCOUNT",
                target.getId(),
                detail + ": " + target.getUsername()
        );
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private UserSummary toSummary(UserAccount userAccount) {
        return UserSummary.builder()
                .userId(userAccount.getId())
                .username(userAccount.getUsername())
                .email(userAccount.getEmail())
                .roles(userAccount.getRoles().stream().toList())
                .managedDomains(userAccount.getManagedDomains().stream().toList())
                .active(userAccount.isActive())
                .locked(userAccount.isLocked())
                .failedLoginAttempts(userAccount.getFailedLoginAttempts())
                .lastLoginAt(userAccount.getLastLoginAt() == null ? null : userAccount.getLastLoginAt().toString())
                .lastPasswordChangedAt(userAccount.getLastPasswordChangedAt() == null ? null : userAccount.getLastPasswordChangedAt().toString())
                .build();
    }

    @Data
    @Builder
    public static class UserSummary {
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
}
