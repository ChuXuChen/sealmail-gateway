package com.sealmail.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dlp_scan_event_recipient", joinColumns = @JoinColumn(name = "event_id"))
    @OrderColumn(name = "position")
    @Column(name = "recipient_email", nullable = false, length = 254)
    private List<String> recipients = new ArrayList<>();

    @Column(name = "subject", length = 1024)
    private String subject;

    @Column(name = "remote_address", length = 128)
    private String remoteAddress;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dlp_scan_event_policy", joinColumns = @JoinColumn(name = "event_id"))
    @OrderColumn(name = "position")
    @Column(name = "policy_id", nullable = false, length = 128)
    private List<String> policyIds = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dlp_scan_event_rule_group", joinColumns = @JoinColumn(name = "event_id"))
    @OrderColumn(name = "position")
    @Column(name = "rule_group_id", nullable = false, length = 128)
    private List<String> ruleGroupIds = new ArrayList<>();

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "max_severity", nullable = false)
    private int maxSeverity;

    @Column(name = "match_count", nullable = false)
    private int matchCount;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dlp_scan_event_extraction_warning", joinColumns = @JoinColumn(name = "event_id"))
    @OrderColumn(name = "position")
    @Column(name = "warning", nullable = false, length = 1024)
    private List<String> extractionWarnings = new ArrayList<>();

    @Column(name = "monitor_mode", nullable = false)
    private boolean monitorMode;

    @Column(name = "scan_duration_ms", nullable = false)
    private long scanDurationMs;

    @Column(name = "uba_risk_level", nullable = false, length = 16)
    private String ubaRiskLevel;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dlp_scan_event_uba_risk_reason", joinColumns = @JoinColumn(name = "event_id"))
    @OrderColumn(name = "position")
    @Column(name = "risk_reason", nullable = false, length = 1024)
    private List<String> ubaRiskReasons = new ArrayList<>();

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
