package com.sealmail.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "quarantine_policy")
public class QuarantinePolicyEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "max_retention_days", nullable = false)
    private int maxRetentionDays;

    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled;

    @Column(name = "release_requires_encryption", nullable = false)
    private boolean releaseRequiresEncryption;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private long version;
}
