package com.sealmail.infra.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "domain_config")
public class DomainConfigEntity {

    @Id
    @Column(name = "id", length = 128)
    private String id;

    @Column(name = "domain_name", nullable = false, length = 254, unique = true)
    private String domainName;

    @Column(name = "is_local", nullable = false)
    private boolean localDomain;

    @Column(name = "encryption_policy", nullable = false, length = 32)
    private String encryptionPolicy;

    @Column(name = "signing_enabled", nullable = false)
    private boolean signingEnabled;

    @Column(name = "dkim_enabled", nullable = false)
    private boolean dkimEnabled;

    @Column(name = "preferred_algorithm", length = 32)
    private String preferredAlgorithm;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private long version;
}
