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

    @Column(name = "regex", columnDefinition = "TEXT")
    private String regex;

    @Column(name = "rule_type", nullable = false, length = 32)
    private String ruleType;

    @Column(name = "builtin_code", length = 128)
    private String builtinCode;

    @Column(name = "content_kinds", columnDefinition = "TEXT")
    private String contentKinds;

    @Column(name = "min_match_count", nullable = false)
    private int minMatchCount;

    @Column(name = "max_evidence_count", nullable = false)
    private int maxEvidenceCount;

    @Column(name = "masking_strategy", nullable = false, length = 32)
    private String maskingStrategy;

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
