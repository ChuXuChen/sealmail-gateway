package com.sealmail.app.usecase.auth;

import com.sealmail.app.dto.request.UserChangePasswordRequest;
import com.sealmail.app.dto.response.UserSummaryResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.audit.AuditService;
import com.sealmail.domain.security.PasswordEncoder;
import com.sealmail.domain.user.UserAccount;
import com.sealmail.domain.user.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserAccountUseCase {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserAccountUseCase(UserAccountRepository userAccountRepository,
                              PasswordEncoder passwordEncoder,
                              AuditService auditService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public void logout(UserContext user, String ipAddress) {
        UserAccount userAccount = requireCurrentUser(user);
        userAccount.invalidateTokens();
        userAccountRepository.save(userAccount);
        auditService.recordUserAction(
                userAccount.getId(),
                userAccount.getUsername(),
                ipAddress,
                AuditLogType.USER_LOGOUT.name(),
                "USER_ACCOUNT",
                userAccount.getId(),
                "用户登出并使现有令牌失效"
        );
    }

    @Transactional
    public void changePassword(UserChangePasswordRequest request, UserContext user, String ipAddress) {
        UserAccount userAccount = requireCurrentUser(user);
        if (!passwordEncoder.matches(request.currentPassword(), userAccount.getPasswordHash())) {
            throw BusinessException.badRequest("当前密码错误");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw BusinessException.badRequest("新密码不能与旧密码相同");
        }
        userAccount.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        userAccountRepository.save(userAccount);
        auditService.recordUserAction(
                userAccount.getId(),
                userAccount.getUsername(),
                ipAddress,
                AuditLogType.USER_PASSWORD_CHANGED.name(),
                "USER_ACCOUNT",
                userAccount.getId(),
                "用户修改自身密码"
        );
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse currentUser(UserContext user) {
        return toSummary(requireCurrentUser(user));
    }

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> list(UserContext operator) {
        requireSuperAdmin(operator);
        return userAccountRepository.findAll().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public UserSummaryResponse disable(String userId, UserContext operator, String ipAddress) {
        requireSuperAdmin(operator);
        UserAccount target = loadUser(userId);
        target.disable();
        target.invalidateTokens();
        userAccountRepository.save(target);
        recordUserUpdate(operator, ipAddress, target, "禁用用户");
        return toSummary(target);
    }

    @Transactional
    public UserSummaryResponse enable(String userId, UserContext operator, String ipAddress) {
        requireSuperAdmin(operator);
        UserAccount target = loadUser(userId);
        target.enable();
        target.invalidateTokens();
        userAccountRepository.save(target);
        recordUserUpdate(operator, ipAddress, target, "启用用户");
        return toSummary(target);
    }

    @Transactional
    public UserSummaryResponse unlock(String userId, UserContext operator, String ipAddress) {
        requireSuperAdmin(operator);
        UserAccount target = loadUser(userId);
        target.unlock();
        target.invalidateTokens();
        userAccountRepository.save(target);
        auditService.recordUserAction(
                operator.getUserId(),
                operator.getUsername(),
                ipAddress,
                AuditLogType.USER_UNLOCKED.name(),
                "USER_ACCOUNT",
                target.getId(),
                "解锁用户: " + target.getUsername()
        );
        return toSummary(target);
    }

    private UserAccount requireCurrentUser(UserContext user) {
        if (user == null || user.getUserId() == null || user.getUserId().isBlank()) {
            throw BusinessException.unauthorized("未登录");
        }
        return userAccountRepository.findById(user.getUserId())
                .orElseThrow(() -> BusinessException.unauthorized("用户不存在"));
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
                                  String ipAddress,
                                  UserAccount target,
                                  String detail) {
        auditService.recordUserAction(
                operator.getUserId(),
                operator.getUsername(),
                ipAddress,
                AuditLogType.USER_UPDATED.name(),
                "USER_ACCOUNT",
                target.getId(),
                detail + ": " + target.getUsername()
        );
    }

    private UserSummaryResponse toSummary(UserAccount userAccount) {
        return UserSummaryResponse.builder()
                .userId(userAccount.getId())
                .username(userAccount.getUsername())
                .email(userAccount.getEmail())
                .roles(userAccount.getRoles().stream().toList())
                .managedDomains(userAccount.getManagedDomains().stream().toList())
                .active(userAccount.isActive())
                .locked(userAccount.isLocked())
                .failedLoginAttempts(userAccount.getFailedLoginAttempts())
                .lastLoginAt(userAccount.getLastLoginAt() == null ? null : userAccount.getLastLoginAt().toString())
                .lastPasswordChangedAt(userAccount.getLastPasswordChangedAt() == null
                        ? null
                        : userAccount.getLastPasswordChangedAt().toString())
                .build();
    }
}
