package com.sealmail.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "dlp_scan_evidence")
public class DlpScanEvidenceEntity {

    @Id
    @Column(name = "id", length = 128)
    private String id;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "rule_id", length = 128)
    private String ruleId;

    @Column(name = "rule_name", nullable = false, length = 128)
    private String ruleName;

    @Column(name = "rule_type", nullable = false, length = 32)
    private String ruleType;

    @Column(name = "part_id", nullable = false, length = 128)
    private String partId;

    @Column(name = "part_kind", nullable = false, length = 32)
    private String partKind;

    @Column(name = "file_name", length = 512)
    private String fileName;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "masked_snippet", length = 1024)
    private String maskedSnippet;

    @Column(name = "match_hash", nullable = false, length = 128)
    private String matchHash;

    @Column(name = "start_offset", nullable = false)
    private int startOffset;

    @Column(name = "end_offset", nullable = false)
    private int endOffset;

    @Column(name = "severity", nullable = false)
    private int severity;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
