package com.sealmail.domain.audit;

public record AuditContext(String userId, String username, String ipAddress) {

    public static AuditContext empty() {
        return new AuditContext(null, null, null);
    }
}
