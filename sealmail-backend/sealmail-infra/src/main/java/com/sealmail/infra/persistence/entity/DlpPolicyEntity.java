package com.sealmail.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    @jakarta.persistence.ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.CollectionTable(name = "dlp_policy_sender_domain", joinColumns = @jakarta.persistence.JoinColumn(name = "policy_id"))
    @jakarta.persistence.OrderColumn(name = "position")
    @Column(name = "domain_name", nullable = false, length = 254)
    private List<String> senderDomains = new ArrayList<>();

    @jakarta.persistence.ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.CollectionTable(name = "dlp_policy_recipient_domain", joinColumns = @jakarta.persistence.JoinColumn(name = "policy_id"))
    @jakarta.persistence.OrderColumn(name = "position")
    @Column(name = "domain_name", nullable = false, length = 254)
    private List<String> recipientDomains = new ArrayList<>();

    @jakarta.persistence.ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.CollectionTable(name = "dlp_policy_sender_address_pattern", joinColumns = @jakarta.persistence.JoinColumn(name = "policy_id"))
    @jakarta.persistence.OrderColumn(name = "position")
    @Column(name = "address_pattern", nullable = false, length = 512)
    private List<String> senderAddressPatterns = new ArrayList<>();

    @jakarta.persistence.ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.CollectionTable(name = "dlp_policy_recipient_address_pattern", joinColumns = @jakarta.persistence.JoinColumn(name = "policy_id"))
    @jakarta.persistence.OrderColumn(name = "position")
    @Column(name = "address_pattern", nullable = false, length = 512)
    private List<String> recipientAddressPatterns = new ArrayList<>();

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
