package com.sealmail.domain.user;

import com.sealmail.domain.shared.model.AggregateRoot;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class UserAccount extends AggregateRoot<String> {

    private String username;
    private String email;
    private String passwordHash;
    private Set<String> roles;
    private Set<String> managedDomains;
    private boolean active;
    private boolean locked;
    private int failedLoginAttempts;
    private Instant lockoutExpiresAt;
    private Instant lastPasswordChangedAt;
    private Instant lastLoginAt;
    private String lastLoginIp;
    private Instant tokenInvalidBefore;

    private UserAccount(String id,
                        String username,
                        String email,
                        String passwordHash,
                        Set<String> roles,
                        Set<String> managedDomains,
                        boolean active,
                        boolean locked,
                        int failedLoginAttempts,
                        Instant lockoutExpiresAt,
                        Instant lastPasswordChangedAt,
                        Instant lastLoginAt,
                        String lastLoginIp,
                        Instant tokenInvalidBefore) {
        super(id);
        this.username = requireText(username, "用户名不能为空");
        this.email = requireText(email, "邮箱不能为空").toLowerCase(Locale.ROOT);
        this.passwordHash = requireText(passwordHash, "密码哈希不能为空");
        this.roles = normalizeRoles(roles);
        this.managedDomains = normalizeDomains(managedDomains);
        this.active = active;
        this.locked = locked;
        this.failedLoginAttempts = Math.max(failedLoginAttempts, 0);
        this.lockoutExpiresAt = lockoutExpiresAt;
        this.lastPasswordChangedAt = lastPasswordChangedAt;
        this.lastLoginAt = lastLoginAt;
        this.lastLoginIp = blankToNull(lastLoginIp);
        this.tokenInvalidBefore = tokenInvalidBefore;
    }

    public static UserAccount create(String id,
                                     String username,
                                     String email,
                                     String passwordHash,
                                     Set<String> roles,
                                     Set<String> managedDomains) {
        return new UserAccount(
                id,
                username,
                email,
                passwordHash,
                roles,
                managedDomains,
                true,
                false,
                0,
                null,
                Instant.now(),
                null,
                null,
                null
        );
    }

    public static UserAccount restore(String id,
                                      String username,
                                      String email,
                                      String passwordHash,
                                      Set<String> roles,
                                      Set<String> managedDomains,
                                      boolean active,
                                      boolean locked,
                                      int failedLoginAttempts,
                                      Instant lockoutExpiresAt,
                                      Instant lastPasswordChangedAt,
                                      Instant lastLoginAt,
                                      String lastLoginIp,
                                      Instant tokenInvalidBefore) {
        return new UserAccount(
                id,
                username,
                email,
                passwordHash,
                roles,
                managedDomains,
                active,
                locked,
                failedLoginAttempts,
                lockoutExpiresAt,
                lastPasswordChangedAt,
                lastLoginAt,
                lastLoginIp,
                tokenInvalidBefore
        );
    }

    public boolean canAuthenticate() {
        if (!active) {
            return false;
        }
        if (!locked) {
            return true;
        }
        if (lockoutExpiresAt == null) {
            return false;
        }
        return lockoutExpiresAt.isBefore(Instant.now());
    }

    public void markLoginSuccess(String ipAddress) {
        this.failedLoginAttempts = 0;
        this.locked = false;
        this.lockoutExpiresAt = null;
        this.lastLoginAt = Instant.now();
        this.lastLoginIp = blankToNull(ipAddress);
    }

    public void markLoginFailed(int maxAttempts) {
        this.failedLoginAttempts = Math.max(0, this.failedLoginAttempts) + 1;
        if (maxAttempts > 0 && this.failedLoginAttempts >= maxAttempts) {
            this.locked = true;
        }
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = requireText(passwordHash, "密码哈希不能为空");
        Instant now = Instant.now();
        this.lastPasswordChangedAt = now;
        this.tokenInvalidBefore = now;
        this.failedLoginAttempts = 0;
        this.locked = false;
        this.lockoutExpiresAt = null;
    }

    public void updateProfile(String username, String email) {
        this.username = requireText(username, "用户名不能为空");
        this.email = requireText(email, "邮箱不能为空").toLowerCase(Locale.ROOT);
    }

    public void replaceRoles(Set<String> roles) {
        this.roles = normalizeRoles(roles);
    }

    public void replaceManagedDomains(Set<String> managedDomains) {
        this.managedDomains = normalizeDomains(managedDomains);
    }

    public void invalidateTokens() {
        this.tokenInvalidBefore = Instant.now();
    }

    public void disable() {
        this.active = false;
    }

    public void enable() {
        this.active = true;
    }

    public void unlock() {
        this.locked = false;
        this.failedLoginAttempts = 0;
        this.lockoutExpiresAt = null;
    }

    public void lock() {
        this.locked = true;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getManagedDomains() {
        return managedDomains;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isLocked() {
        return locked;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockoutExpiresAt() {
        return lockoutExpiresAt;
    }

    public Instant getLastPasswordChangedAt() {
        return lastPasswordChangedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public String getLastLoginIp() {
        return lastLoginIp;
    }

    public Instant getTokenInvalidBefore() {
        return tokenInvalidBefore;
    }

    private static Set<String> normalizeRoles(Set<String> roles) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (roles == null) {
            return normalized;
        }
        for (String role : roles) {
            String value = blankToNull(role);
            if (value != null) {
                normalized.add(value.toUpperCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    private static Set<String> normalizeDomains(Set<String> domains) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (domains == null) {
            return normalized;
        }
        for (String domain : domains) {
            String value = blankToNull(domain);
            if (value != null) {
                normalized.add(value.toLowerCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    private static String requireText(String value, String message) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
