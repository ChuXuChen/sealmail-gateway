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
@Table(name = "certificate_binding")
public class CertificateBindingEntity {

    @Id
    @Column(name = "id", length = 128)
    private String id;

    @Column(name = "domain_name", nullable = false, length = 253)
    private String domainName;

    @Column(name = "owner_email", nullable = false, length = 254)
    private String ownerEmail;

    @Column(name = "purpose", nullable = false, length = 32)
    private String purpose;

    @Column(name = "certificate_id", nullable = false, length = 128)
    private String certificateId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private long version;
}
