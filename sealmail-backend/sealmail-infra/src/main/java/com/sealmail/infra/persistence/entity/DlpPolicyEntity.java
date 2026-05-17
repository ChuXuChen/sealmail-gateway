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
@Table(name = "dlp_policy")
public class DlpPolicyEntity {

    @Id
    @Column(name = "id", length = 128)
    private String id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "mode", nullable = false, length = 32)
    private String mode;

    @Column(name = "direction", length = 16)
    private String direction;

    @Column(name = "sender_domains", columnDefinition = "TEXT")
    private String senderDomains;

    @Column(name = "recipient_domains", columnDefinition = "TEXT")
    private String recipientDomains;

    @Column(name = "sender_address_patterns", columnDefinition = "TEXT")
    private String senderAddressPatterns;

    @Column(name = "recipient_address_patterns", columnDefinition = "TEXT")
    private String recipientAddressPatterns;

    @Column(name = "attachment_required", nullable = false)
    private boolean attachmentRequired;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private long version;
}
