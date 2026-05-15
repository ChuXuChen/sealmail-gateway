package com.sealmail.infra.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "dlp_quarantine_mail")
public class QuarantinedMailEntity {

    @Id
    @Column(name = "id", length = 128)
    private String id;

    @Column(name = "message_id", nullable = false, length = 254)
    private String messageId;

    @Column(name = "subject", length = 1024)
    private String subject;

    @Column(name = "sender_email", nullable = false, length = 254)
    private String senderEmail;

    @Column(name = "recipients", nullable = false, length = 2048)
    private String recipients;

    @Column(name = "direction", length = 16)
    private String direction;

    @Column(name = "remote_address", length = 128)
    private String remoteAddress;

    @Column(name = "reason", nullable = false, length = 64)
    private String reason;

    @Column(name = "detail", length = 1024)
    private String detail;

    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String rawContent;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "processed_by", length = 254)
    private String processedBy;

    @Column(name = "process_comment", length = 1024)
    private String processComment;

    @Version
    @Column(name = "version")
    private long version;
}
