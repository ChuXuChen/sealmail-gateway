package com.sealmail.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "dlp_scan_event")
public class DlpScanEventEntity {

    @Id
    @Column(name = "id", length = 128)
    private String id;

    @Column(name = "message_id", length = 254)
    private String messageId;

    @Column(name = "processing_id", length = 128)
    private String processingId;

    @Column(name = "direction", length = 16)
    private String direction;

    @Column(name = "sender_email", length = 254)
    private String senderEmail;

    @Column(name = "recipients", columnDefinition = "TEXT")
    private String recipients;

    @Column(name = "subject", length = 1024)
    private String subject;

    @Column(name = "remote_address", length = 128)
    private String remoteAddress;

    @Column(name = "policy_ids", columnDefinition = "TEXT")
    private String policyIds;

    @Column(name = "rule_group_ids", columnDefinition = "TEXT")
    private String ruleGroupIds;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "max_severity", nullable = false)
    private int maxSeverity;

    @Column(name = "match_count", nullable = false)
    private int matchCount;

    @Column(name = "extraction_warnings", columnDefinition = "TEXT")
    private String extractionWarnings;

    @Column(name = "monitor_mode", nullable = false)
    private boolean monitorMode;

    @Column(name = "scan_duration_ms", nullable = false)
    private long scanDurationMs;

    @Column(name = "uba_risk_level", nullable = false, length = 16)
    private String ubaRiskLevel;

    @Column(name = "uba_risk_reasons", columnDefinition = "TEXT")
    private String ubaRiskReasons;

    @Column(name = "uba_action_upgraded", nullable = false)
    private boolean ubaActionUpgraded;

    @Column(name = "quarantine_id", length = 128)
    private String quarantineId;

    @Column(name = "false_positive", nullable = false)
    private boolean falsePositive;

    @Column(name = "false_positive_at")
    private Instant falsePositiveAt;

    @Column(name = "false_positive_by", length = 254)
    private String falsePositiveBy;

    @Column(name = "false_positive_comment", length = 1024)
    private String falsePositiveComment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
