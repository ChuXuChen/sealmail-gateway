package com.sealmail.app.security;

public enum AppPermission {
    MANAGE_CA("manage CA resources"),
    REVIEW_CSR("review CSR requests"),
    VIEW_ALL_CERTIFICATES("view all certificates"),
    MANAGE_QUARANTINE("manage quarantine items"),
    VIEW_QUARANTINE("view quarantine items"),
    MANAGE_RUNTIME_POLICY("manage runtime policy"),
    OPERATE_MAIL_TOOLS("operate mail tools"),
    VIEW_SYSTEM_SETTINGS("view system settings");

    private final String description;

    AppPermission(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
