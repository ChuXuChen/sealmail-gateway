package com.sealmail.infra.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_type", columnList = "type"),
        @Index(name = "idx_audit_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_occurred_at", columnList = "occurred_at"),
        @Index(name = "idx_audit_resource", columnList = "resource_type, resource_id")
})
public class AuditLogEntity {

    @Id
    @Column(name = "id", length = 128)
    private String id;

    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Column(name = "user_id", length = 128)
    private String userId;

    @Column(name = "username", length = 64)
    private String username;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "resource_type", length = 64)
    private String resourceType;

    @Column(name = "resource_id", length = 128)
    private String resourceId;

    @Column(name = "action", length = 64)
    private String action;

    @Column(name = "detail", length = 2048)
    private String detail;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
