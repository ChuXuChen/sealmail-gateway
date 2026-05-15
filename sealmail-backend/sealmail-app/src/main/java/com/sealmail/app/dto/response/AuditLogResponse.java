package com.sealmail.app.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 审计日志响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private String id;
    private String type;
    private String typeDisplayName;
    private String userId;
    private String username;
    private String ipAddress;
    private String resourceType;
    private String resourceId;
    private String action;
    private String detail;
    private boolean success;
    private String errorMessage;
    private Instant occurredAt;
}
