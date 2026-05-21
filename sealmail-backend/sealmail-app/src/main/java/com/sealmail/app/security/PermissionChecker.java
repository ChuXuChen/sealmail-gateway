package com.sealmail.app.security;

import com.sealmail.app.exception.SecurityException;
import org.springframework.stereotype.Component;

@Component
public class PermissionChecker {

    private final AppPermissionEvaluator permissionEvaluator;

    public PermissionChecker(AppPermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    public void checkAuthenticated(UserContext user) {
        if (user == null) {
            throw SecurityException.unauthorized("User not authenticated");
        }
    }

    public void check(UserContext user, AppPermission permission) {
        check(user, permission, "User does not have permission to " + permission.description());
    }

    public void check(UserContext user, AppPermission permission, String message) {
        checkAuthenticated(user);
        if (!permissionEvaluator.hasPermission(user, permission)) {
            throw SecurityException.accessDenied(message);
        }
    }

    public void checkCanManageCa(UserContext user) {
        check(user, AppPermission.MANAGE_CA, "User does not have permission to manage CA resources");
    }

    public void checkCanReviewCsr(UserContext user) {
        check(user, AppPermission.REVIEW_CSR, "User does not have permission to review CSR requests");
    }

    public void checkCanManageCertificates(UserContext user, String ownerDomain) {
        checkAuthenticated(user);
        if (!user.canManageDomain(ownerDomain)) {
            throw SecurityException.accessDenied(
                    "User does not have permission to manage certificates for domain: " + ownerDomain);
        }
    }

    public void checkCanViewCertificates(UserContext user, String ownerDomain) {
        checkAuthenticated(user);
        if (!user.canViewDomain(ownerDomain)) {
            throw SecurityException.accessDenied(
                    "User does not have permission to view certificates for domain: " + ownerDomain);
        }
    }

    public void checkCanViewAllCertificates(UserContext user) {
        check(user, AppPermission.VIEW_ALL_CERTIFICATES, "User does not have permission to view all certificates");
    }

    public void checkCanManageQuarantine(UserContext user) {
        check(user, AppPermission.MANAGE_QUARANTINE, "Only administrators can manage quarantine items");
    }

    public void checkCanViewQuarantine(UserContext user) {
        check(user, AppPermission.VIEW_QUARANTINE, "Only administrators or auditors can view quarantine items");
    }

    public void checkCanManageRuntimePolicy(UserContext user) {
        check(user, AppPermission.MANAGE_RUNTIME_POLICY, "User does not have permission to manage runtime policy");
    }

    public void checkCanOperateMailTools(UserContext user) {
        check(user, AppPermission.OPERATE_MAIL_TOOLS, "User does not have permission to operate mail tools");
    }

    public void checkCanViewSystemSettings(UserContext user) {
        check(user, AppPermission.VIEW_SYSTEM_SETTINGS, "User does not have permission to view system settings");
    }
}
