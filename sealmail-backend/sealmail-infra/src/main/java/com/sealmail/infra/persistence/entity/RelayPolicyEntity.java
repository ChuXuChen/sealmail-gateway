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
@Table(name = "relay_policy")
public class RelayPolicyEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "host", nullable = false, length = 254)
    private String host;

    @Column(name = "port", nullable = false)
    private int port;

    @Column(name = "username", length = 254)
    private String username;

    @Column(name = "password_secret_ref", length = 1024)
    private String passwordSecretRef;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    @Column(name = "envelope_from", length = 254)
    private String envelopeFrom;

    @Column(name = "allow_unconfigured_external_recipient_domains", nullable = false)
    private boolean allowUnconfiguredExternalRecipientDomains;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private long version;
}
