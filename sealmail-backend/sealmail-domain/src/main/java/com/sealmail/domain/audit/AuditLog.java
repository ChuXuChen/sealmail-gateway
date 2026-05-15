package com.sealmail.domain.audit;

import com.sealmail.domain.shared.model.AggregateRoot;

import java.time.Instant;
import java.util.UUID;

/**
 * 审计日志聚合根
 */
public class AuditLog extends AggregateRoot<String> {

    private final AuditLogType type;
    private final String userId;
    private final String username;
    private final String ipAddress;
    private final String resourceType;
    private final String resourceId;
    private final String action;
    private final String detail;
    private final boolean success;
    private final String errorMessage;
    private final Instant occurredAt;

    private AuditLog(Builder builder) {
        super(builder.id);
        this.type = builder.type;
        this.userId = builder.userId;
        this.username = builder.username;
        this.ipAddress = builder.ipAddress;
        this.resourceType = builder.resourceType;
        this.resourceId = builder.resourceId;
        this.action = builder.action;
        this.detail = builder.detail;
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
        this.occurredAt = builder.occurredAt != null ? builder.occurredAt : Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AuditLog create(AuditLogType type, String userId, String username, String ipAddress) {
        return builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .userId(userId)
                .username(username)
                .ipAddress(ipAddress)
                .success(true)
                .build();
    }

    public static AuditLog failed(AuditLogType type, String userId, String username, String ipAddress, String errorMessage) {
        return builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .userId(userId)
                .username(username)
                .ipAddress(ipAddress)
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    public AuditLogType getType() {
        return type;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public static class Builder {
        private String id;
        private AuditLogType type;
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

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(AuditLogType type) {
            this.type = type;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder detail(String detail) {
            this.detail = detail;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public AuditLog build() {
            if (type == null) {
                throw new IllegalArgumentException("Audit log type cannot be null");
            }
            return new AuditLog(this);
        }
    }
}
