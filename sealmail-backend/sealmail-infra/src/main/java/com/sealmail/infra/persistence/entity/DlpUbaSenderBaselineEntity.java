package com.sealmail.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "dlp_uba_sender_baseline")
public class DlpUbaSenderBaselineEntity {

    @Id
    @Column(name = "sender_email", length = 254)
    private String senderEmail;

    @Column(name = "total_messages", nullable = false)
    private long totalMessages;

    @Column(name = "outbound_messages", nullable = false)
    private long outboundMessages;

    @Column(name = "external_domains", columnDefinition = "TEXT")
    private String externalDomains;

    @Column(name = "active_hours", columnDefinition = "TEXT")
    private String activeHours;

    @Column(name = "max_attachment_bytes", nullable = false)
    private long maxAttachmentBytes;

    @Column(name = "dlp_hit_count", nullable = false)
    private long dlpHitCount;

    @Column(name = "high_risk_count", nullable = false)
    private long highRiskCount;

    @Column(name = "last_risk_level", nullable = false, length = 16)
    private String lastRiskLevel;

    @Column(name = "last_risk_reasons", columnDefinition = "TEXT")
    private String lastRiskReasons;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
