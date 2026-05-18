package com.sealmail.infra.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    @Column(name = "all_patterns", nullable = false)
    private boolean allPatterns;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dlp_selection_pattern", joinColumns = @JoinColumn(name = "selection_id"))
    @OrderColumn(name = "position")
    @Column(name = "pattern_id", nullable = false, length = 128)
    private List<String> patternIds = new ArrayList<>();

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
