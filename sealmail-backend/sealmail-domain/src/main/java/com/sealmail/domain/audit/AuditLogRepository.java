package com.sealmail.domain.audit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 审计日志仓储接口
 */
public interface AuditLogRepository {

    AuditLog save(AuditLog auditLog);

    Optional<AuditLog> findById(String id);

    List<AuditLog> findByType(AuditLogType type, int page, int size);

    List<AuditLog> findByUserId(String userId, int page, int size);

    List<AuditLog> findByResource(String resourceType, String resourceId, int page, int size);

    List<AuditLog> findByTimeRange(Instant startTime, Instant endTime, int page, int size);

    List<AuditLog> findAll(int page, int size);

    List<AuditLog> search(List<AuditLogType> types, Boolean success, int page, int size);

    long count();

    long countByType(AuditLogType type);

    long countByUserId(String userId);

    long countByResource(String resourceType, String resourceId);

    long countByTimeRange(Instant startTime, Instant endTime);

    long countSearch(List<AuditLogType> types, Boolean success);
}
