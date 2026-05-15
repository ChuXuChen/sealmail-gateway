package com.sealmail.domain.shared.event;

public class AuditEvent extends DomainEvent {

    private final String eventType;
    private final String userId;
    private final String username;
    private final String resourceType;
    private final String resourceId;
    private final String action;
    private final String description;
    private final String ipAddress;
    private final boolean success;

    private AuditEvent(Builder builder) {
        super();
        this.eventType = builder.eventType;
        this.userId = builder.userId;
        this.username = builder.username;
        this.resourceType = builder.resourceType;
        this.resourceId = builder.resourceId;
        this.action = builder.action;
        this.description = builder.description;
        this.ipAddress = builder.ipAddress;
        this.success = builder.success;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getEventType() {
        return eventType;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
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

    public String getDescription() {
        return description;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public boolean isSuccess() {
        return success;
    }

    public static class Builder {
        private String eventType;
        private String userId;
        private String username;
        private String resourceType;
        private String resourceId;
        private String action;
        private String description;
        private String ipAddress;
        private boolean success = true;

        public Builder eventType(String eventType) {
            this.eventType = eventType;
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

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public AuditEvent build() {
            return new AuditEvent(this);
        }
    }
}
