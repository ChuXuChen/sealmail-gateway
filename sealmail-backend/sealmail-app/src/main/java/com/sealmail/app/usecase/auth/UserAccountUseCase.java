package com.sealmail.app.usecase.auth;

import com.sealmail.app.dto.request.CreateUserRequest;
import com.sealmail.app.dto.request.UpdateUserRequest;
import com.sealmail.app.dto.request.UserChangePasswordRequest;
import com.sealmail.app.dto.request.UserProfileUpdateRequest;
import com.sealmail.app.dto.request.UserResetPasswordRequest;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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
        String currentPassword = requireText(request.currentPassword(), "当前密码不能为空");
        String newPassword = requireText(request.newPassword(), "新密码不能为空");
        if (!passwordEncoder.matches(currentPassword, userAccount.getPasswordHash())) {
            throw BusinessException.badRequest("当前密码错误");
        }
        if (currentPassword.equals(newPassword)) {
            throw BusinessException.badRequest("新密码不能与旧密码相同");
        }
        userAccount.changePasswordHash(passwordEncoder.encode(newPassword));
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

    @Transactional
    public UserSummaryResponse updateProfile(UserProfileUpdateRequest request, UserContext user, String ipAddress) {
        UserAccount userAccount = requireCurrentUser(user);
        String username = requireText(request.username(), "用户名不能为空");
        String email = normalizeEmail(request.email());
        ensureUniqueIdentity(userAccount.getId(), username, email);

        boolean changed = !Objects.equals(userAccount.getUsername(), username)
                || !Objects.equals(userAccount.getEmail(), email);
        if (changed) {
            userAccount.updateProfile(username, email);
            userAccount.invalidateTokens();
            userAccountRepository.save(userAccount);
        }
        auditService.recordUserAction(
                userAccount.getId(),
                userAccount.getUsername(),
                ipAddress,
                AuditLogType.USER_UPDATED.name(),
                "USER_ACCOUNT",
                userAccount.getId(),
                "用户修改自身资料"
        );
        return toSummary(userAccount);
    }

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> list(UserContext operator) {
        return list(operator, "all");
    }

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> list(UserContext operator, String status) {
        requireSuperAdmin(operator);
        String normalizedStatus = status == null || status.isBlank()
                ? "all"
                : status.trim().toLowerCase(Locale.ROOT);
        return userAccountRepository.findAll().stream()
                .filter(userAccount -> switch (normalizedStatus) {
                    case "active" -> userAccount.isActive();
                    case "disabled" -> !userAccount.isActive();
                    case "all" -> true;
                    default -> throw BusinessException.badRequest("用户状态筛选仅支持 active、disabled 或 all");
                })
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public UserSummaryResponse create(CreateUserRequest request, UserContext operator, String ipAddress) {
        requireSuperAdmin(operator);
        String username = requireText(request.username(), "用户名不能为空");
        String email = normalizeEmail(request.email());
        String password = requireText(request.password(), "密码不能为空");
        ensureUniqueIdentity(null, username, email);

        UserAccount target = UserAccount.create(
                UUID.randomUUID().toString(),
                username,
                email,
                passwordEncoder.encode(password),
                normalizeRoles(request.roles()),
                normalizeDomains(request.managedDomains())
        );
        userAccountRepository.save(target);
        auditService.recordUserAction(
                operator.getUserId(),
                operator.getUsername(),
                ipAddress,
                AuditLogType.USER_CREATED.name(),
                "USER_ACCOUNT",
                target.getId(),
                "创建用户: " + target.getUsername()
        );
        return toSummary(target);
    }

    @Transactional
    public UserSummaryResponse update(String userId, UpdateUserRequest request, UserContext operator, String ipAddress) {
        requireSuperAdmin(operator);
        UserAccount target = loadUser(userId);
        String username = requireText(request.username(), "用户名不能为空");
        String email = normalizeEmail(request.email());
        ensureUniqueIdentity(target.getId(), username, email);

        Set<String> newRoles = request.roles() == null ? new LinkedHashSet<>(target.getRoles()) : normalizeRoles(request.roles());
        Set<String> newManagedDomains = request.managedDomains() == null
                ? new LinkedHashSet<>(target.getManagedDomains())
                : normalizeDomains(request.managedDomains());
        boolean rolesChanged = !target.getRoles().equals(newRoles);
        boolean managedDomainsChanged = !target.getManagedDomains().equals(newManagedDomains);
        boolean profileChanged = !Objects.equals(target.getUsername(), username)
                || !Objects.equals(target.getEmail(), email);
        boolean targetIsOperator = Objects.equals(target.getId(), operator.getUserId());

        if (rolesChanged) {
            validateRoleChange(target, newRoles, targetIsOperator);
        }

        boolean activeChanged = false;
        if (request.active() != null && request.active() != target.isActive()) {
            if (!request.active()) {
                validateDisable(target, operator);
                target.disable();
            } else {
                target.enable();
            }
            activeChanged = true;
        }

        boolean lockedChanged = false;
        if (request.locked() != null && request.locked() != target.isLocked()) {
            if (request.locked()) {
                target.lock();
            } else {
                target.unlock();
            }
            lockedChanged = true;
        }

        if (profileChanged) {
            target.updateProfile(username, email);
        }
        if (rolesChanged) {
            target.replaceRoles(newRoles);
        }
        if (managedDomainsChanged) {
            target.replaceManagedDomains(newManagedDomains);
        }
        if (profileChanged || rolesChanged || managedDomainsChanged || activeChanged || lockedChanged) {
            target.invalidateTokens();
            userAccountRepository.save(target);
        }
        if (rolesChanged) {
            auditService.recordUserAction(
                    operator.getUserId(),
                    operator.getUsername(),
                    ipAddress,
                    AuditLogType.USER_ROLE_CHANGED.name(),
                    "USER_ACCOUNT",
                    target.getId(),
                    "变更用户角色: " + target.getUsername()
            );
        }
        if (profileChanged || managedDomainsChanged || activeChanged || lockedChanged) {
            recordUserUpdate(operator, ipAddress, target, "更新用户");
        }
        return toSummary(target);
    }

    @Transactional
    public UserSummaryResponse disable(String userId, UserContext operator, String ipAddress) {
        requireSuperAdmin(operator);
        UserAccount target = loadUser(userId);
        validateDisable(target, operator);
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

    @Transactional
    public UserSummaryResponse resetPassword(String userId,
                                             UserResetPasswordRequest request,
                                             UserContext operator,
                                             String ipAddress) {
        requireSuperAdmin(operator);
        UserAccount target = loadUser(userId);
        String newPassword = requireText(request.newPassword(), "新密码不能为空");
        target.changePasswordHash(passwordEncoder.encode(newPassword));
        userAccountRepository.save(target);
        auditService.recordUserAction(
                operator.getUserId(),
                operator.getUsername(),
                ipAddress,
                AuditLogType.USER_PASSWORD_CHANGED.name(),
                "USER_ACCOUNT",
                target.getId(),
                "管理员重置用户密码: " + target.getUsername()
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

    private void ensureUniqueIdentity(String currentUserId, String username, String email) {
        userAccountRepository.findByUsername(username)
                .filter(existing -> !Objects.equals(existing.getId(), currentUserId))
                .ifPresent(existing -> {
                    throw BusinessException.conflict("用户名已存在");
                });
        userAccountRepository.findByEmail(email)
                .filter(existing -> !Objects.equals(existing.getId(), currentUserId))
                .ifPresent(existing -> {
                    throw BusinessException.conflict("邮箱已存在");
                });
    }

    private void validateDisable(UserAccount target, UserContext operator) {
        if (Objects.equals(target.getId(), operator.getUserId())) {
            throw BusinessException.badRequest("不能停用当前登录用户");
        }
        if (target.isActive() && isSuperAdmin(target)) {
            requireAnotherActiveSuperAdmin(target.getId());
        }
    }

    private void validateRoleChange(UserAccount target, Set<String> newRoles, boolean targetIsOperator) {
        boolean hadSuperAdmin = isSuperAdmin(target);
        boolean keepsSuperAdmin = containsRole(newRoles, "SUPER_ADMIN");
        if (targetIsOperator && hadSuperAdmin && !keepsSuperAdmin) {
            throw BusinessException.badRequest("不能移除自己的 SUPER_ADMIN 角色");
        }
        if (target.isActive() && hadSuperAdmin && !keepsSuperAdmin) {
            requireAnotherActiveSuperAdmin(target.getId());
        }
    }

    private void requireAnotherActiveSuperAdmin(String targetUserId) {
        boolean hasAnother = userAccountRepository.findAll().stream()
                .anyMatch(userAccount -> userAccount.isActive()
                        && !Objects.equals(userAccount.getId(), targetUserId)
                        && isSuperAdmin(userAccount));
        if (!hasAnother) {
            throw BusinessException.badRequest("不能停用或降权最后一个超级管理员");
        }
    }

    private boolean isSuperAdmin(UserAccount userAccount) {
        return containsRole(userAccount.getRoles(), "SUPER_ADMIN");
    }

    private boolean containsRole(Set<String> roles, String role) {
        if (roles == null || role == null) {
            return false;
        }
        return roles.stream().anyMatch(role::equalsIgnoreCase);
    }

    private Set<String> normalizeRoles(List<String> roles) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (roles != null) {
            for (String role : roles) {
                String value = blankToNull(role);
                if (value != null) {
                    normalized.add(value.toUpperCase(Locale.ROOT));
                }
            }
        }
        if (normalized.isEmpty()) {
            normalized.add("USER");
        }
        return normalized;
    }

    private Set<String> normalizeDomains(List<String> domains) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (domains != null) {
            for (String domain : domains) {
                String value = blankToNull(domain);
                if (value != null) {
                    normalized.add(value.toLowerCase(Locale.ROOT));
                }
            }
        }
        return normalized;
    }

    private String normalizeEmail(String value) {
        return requireText(value, "邮箱不能为空").toLowerCase(Locale.ROOT);
    }

    private String requireText(String value, String message) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw BusinessException.badRequest(message);
        }
        return normalized;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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
