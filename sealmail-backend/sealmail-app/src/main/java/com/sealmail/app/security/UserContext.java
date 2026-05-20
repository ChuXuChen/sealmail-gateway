package com.sealmail.app.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContext {

    private String userId;
    private String username;
    private String email;
    @Builder.Default
    private Set<String> roles = new LinkedHashSet<>();
    @Builder.Default
    private Set<String> managedDomains = new LinkedHashSet<>();

    public boolean isAdmin() {
        return hasRole("SUPER_ADMIN") || hasRole("PKI_ADMIN") || hasRole("ADMIN");
    }

    public boolean isAuditor() {
        return hasRole("AUDITOR");
    }

    public boolean canReviewCsr() {
        return isAdmin();
    }

    public boolean canManageCa() {
        return isAdmin();
    }

    public boolean canViewAllCertificates() {
        return isAdmin() || isAuditor();
    }

    public boolean canManageQuarantine() {
        return isAdmin();
    }

    public boolean canViewQuarantine() {
        return isAdmin() || isAuditor();
    }

    public boolean canManageRuntimePolicy() {
        return isAdmin() || hasRole("RUNTIME_POLICY_ADMIN");
    }

    public boolean canOperateMailTools() {
        return isAdmin() || hasRole("MAIL_OPERATOR");
    }

    public boolean canViewSystemSettings() {
        return isAdmin() || hasRole("SYSTEM_VIEWER");
    }

    public boolean canManageDomain(String domain) {
        if (isAdmin()) {
            return true;
        }
        if (!hasRole("DOMAIN_ADMIN") && !hasRole("DOMAIN_MANAGER")) {
            return false;
        }
        String normalized = normalizeDomain(domain);
        return normalized != null && managedDomains.stream()
                .map(this::normalizeDomain)
                .anyMatch(normalized::equals);
    }

    public boolean canViewDomain(String domain) {
        return canViewAllCertificates() || canManageDomain(domain);
    }

    public String getPrimaryRole() {
        return roles.stream().findFirst().orElse("USER");
    }

    public boolean hasRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        return roles.stream().anyMatch(existing -> role.equalsIgnoreCase(existing));
    }

    private String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        return domain.toLowerCase(Locale.ROOT);
    }
}
