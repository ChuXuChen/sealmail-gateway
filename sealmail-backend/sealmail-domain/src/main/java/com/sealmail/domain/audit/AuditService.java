package com.sealmail.domain.audit;

/**
 * 审计服务 - 领域服务
 */
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void recordLogin(String userId, String username, String ipAddress) {
        AuditLog log = AuditLog.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type(AuditLogType.USER_LOGIN)
                .userId(userId)
                .username(username)
                .ipAddress(ipAddress)
                .action("USER_LOGIN")
                .success(true)
                .build();
        repository.save(log);
    }

    public void recordLoginFailed(String username, String ipAddress, String errorMessage) {
        AuditLog log = AuditLog.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type(AuditLogType.USER_LOGIN_FAILED)
                .username(username)
                .ipAddress(ipAddress)
                .action("USER_LOGIN_FAILED")
                .success(false)
                .errorMessage(errorMessage)
                .build();
        repository.save(log);
    }

    public void recordUserAction(String userId, String username, String ipAddress,
                                 String action, String resourceType, String resourceId, String detail) {
        AuditLog log = AuditLog.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type(resolveType(action))
                .userId(userId)
                .username(username)
                .ipAddress(ipAddress)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .detail(detail)
                .success(true)
                .build();
        repository.save(log);
    }

    private AuditLogType resolveType(String action) {
        if (action == null || action.isBlank()) {
            return AuditLogType.OTHER;
        }
        try {
            return AuditLogType.valueOf(action);
        } catch (IllegalArgumentException e) {
            return AuditLogType.OTHER;
        }
    }

    public void recordEmailAction(AuditLogType type, String userId, String username,
                                  String ipAddress, String messageId, String detail) {
        AuditLog log = AuditLog.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type(type)
                .userId(userId)
                .username(username)
                .ipAddress(ipAddress)
                .action(type.name())
                .resourceType("EMAIL")
                .resourceId(messageId)
                .detail(detail)
                .success(true)
                .build();
        repository.save(log);
    }

    public void recordCertificateAction(AuditLogType type, String userId, String username,
                                        String ipAddress, String certificateId, String detail) {
        AuditLog log = AuditLog.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type(type)
                .userId(userId)
                .username(username)
                .ipAddress(ipAddress)
                .action(type.name())
                .resourceType("CERTIFICATE")
                .resourceId(certificateId)
                .detail(detail)
                .success(true)
                .build();
        repository.save(log);
    }
}
