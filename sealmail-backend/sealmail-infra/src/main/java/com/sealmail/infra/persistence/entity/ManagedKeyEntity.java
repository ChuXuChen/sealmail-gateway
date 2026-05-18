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
@Table(name = "managed_key")
public class ManagedKeyEntity {

    @Id
    @Column(name = "key_id", length = 128)
    private String keyId;

    @Column(name = "owner_email", nullable = false, length = 254)
    private String ownerEmail;

    @Column(name = "algorithm", nullable = false, length = 64)
    private String algorithm;

    @Column(name = "purpose", nullable = false, length = 32)
    private String purpose;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "provider_ref", nullable = false, length = 1024)
    private String providerRef;

    @Column(name = "certificate_id", length = 128)
    private String certificateId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "rotated_from_key_id", length = 128)
    private String rotatedFromKeyId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private long version;
}
