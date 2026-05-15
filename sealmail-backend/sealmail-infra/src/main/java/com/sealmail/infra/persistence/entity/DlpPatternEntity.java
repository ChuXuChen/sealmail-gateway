package com.sealmail.infra.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "dlp_pattern")
public class DlpPatternEntity {

    @Id
    @Column(name = "id", length = 128)
    private String id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "regex", nullable = false, columnDefinition = "TEXT")
    private String regex;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "severity", nullable = false)
    private int severity;

    @Column(name = "priority", nullable = false)
    private int priority;

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
