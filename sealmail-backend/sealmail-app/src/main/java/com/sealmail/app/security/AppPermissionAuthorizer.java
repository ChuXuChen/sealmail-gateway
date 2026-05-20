package com.sealmail.app.security;

import org.springframework.stereotype.Component;

@Component
public class AppPermissionAuthorizer {

    private final AppPermissionEvaluator evaluator;

    public AppPermissionAuthorizer(AppPermissionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public boolean canManageRuntimePolicy(UserContext user) {
        return evaluator.hasPermission(user, AppPermission.MANAGE_RUNTIME_POLICY);
    }

    public boolean canOperateMailTools(UserContext user) {
        return evaluator.hasPermission(user, AppPermission.OPERATE_MAIL_TOOLS);
    }

    public boolean canViewSystemSettings(UserContext user) {
        return evaluator.hasPermission(user, AppPermission.VIEW_SYSTEM_SETTINGS);
    }
}
