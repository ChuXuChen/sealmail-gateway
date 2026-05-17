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
@Table(name = "smime_suite_policy")
public class SmimeSuitePolicyEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "default_standard_suite", nullable = false, length = 128)
    private String defaultStandardSuite;

    @Column(name = "default_gm_suite", nullable = false, length = 128)
    private String defaultGmSuite;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private long version;
}
