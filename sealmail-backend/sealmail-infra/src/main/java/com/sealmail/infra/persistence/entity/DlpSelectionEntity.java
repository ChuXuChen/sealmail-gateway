package com.sealmail.infra.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "dlp_selection")
public class DlpSelectionEntity {

    @Id
    @Column(name = "id", length = 128)
    private String id;

    @Column(name = "scope_type", nullable = false, length = 32)
    private String scopeType;

    @Column(name = "scope_value", length = 254)
    private String scopeValue;

    @Column(name = "pattern_ids", columnDefinition = "TEXT")
    private String patternIds;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private long version;
}
