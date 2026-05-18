package com.sealmail.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "dlp_edm_value")
public class DlpEdmValueEntity {

    @Id
    @Column(name = "id", length = 128)
    private String id;

    @Column(name = "dataset_id", nullable = false, length = 128)
    private String datasetId;

    @Column(name = "value_hash", nullable = false, length = 64)
    private String valueHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
