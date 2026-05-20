package com.sealmail.app.security;

import org.springframework.stereotype.Component;

@Component
public class AppPermissionEvaluator {

    public boolean hasPermission(UserContext user, AppPermission permission) {
        if (user == null || permission == null) {
            return false;
        }
        return switch (permission) {
            case MANAGE_CA -> user.canManageCa();
            case REVIEW_CSR -> user.canReviewCsr();
            case VIEW_ALL_CERTIFICATES -> user.canViewAllCertificates();
            case MANAGE_QUARANTINE -> user.canManageQuarantine();
            case VIEW_QUARANTINE -> user.canViewQuarantine();
            case MANAGE_RUNTIME_POLICY -> user.canManageRuntimePolicy();
            case OPERATE_MAIL_TOOLS -> user.canOperateMailTools();
            case VIEW_SYSTEM_SETTINGS -> user.canViewSystemSettings();
        };
    }
}
