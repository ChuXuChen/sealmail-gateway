package com.sealmail.infra.persistence.mapper;

import com.sealmail.domain.audit.AuditLog;
import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.infra.persistence.entity.AuditLogEntity;
import org.springframework.stereotype.Component;

@Component
public class AuditLogMapper {

    public AuditLogEntity toEntity(AuditLog log) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(log.getId());
        entity.setType(log.getType().name());
        entity.setUserId(log.getUserId());
        entity.setUsername(log.getUsername());
        entity.setIpAddress(log.getIpAddress());
        entity.setResourceType(log.getResourceType());
        entity.setResourceId(log.getResourceId());
        entity.setAction(log.getAction());
        entity.setDetail(log.getDetail());
        entity.setSuccess(log.isSuccess());
        entity.setErrorMessage(log.getErrorMessage());
        entity.setOccurredAt(log.getOccurredAt());
        return entity;
    }

    public AuditLog toDomain(AuditLogEntity entity) {
        return AuditLog.builder()
                .id(entity.getId())
                .type(AuditLogType.valueOf(entity.getType()))
                .userId(entity.getUserId())
                .username(entity.getUsername())
                .ipAddress(entity.getIpAddress())
                .resourceType(entity.getResourceType())
                .resourceId(entity.getResourceId())
                .action(entity.getAction())
                .detail(entity.getDetail())
                .success(entity.isSuccess())
                .errorMessage(entity.getErrorMessage())
                .occurredAt(entity.getOccurredAt())
                .build();
    }
}
