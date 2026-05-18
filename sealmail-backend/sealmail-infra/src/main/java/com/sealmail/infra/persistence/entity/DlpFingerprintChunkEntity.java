package com.sealmail.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "dlp_fingerprint_chunk")
public class DlpFingerprintChunkEntity {

    @Id
    @Column(name = "id", length = 128)
    private String id;

    @Column(name = "library_id", nullable = false, length = 128)
    private String libraryId;

    @Column(name = "document_id", nullable = false, length = 128)
    private String documentId;

    @Column(name = "document_name", length = 255)
    private String documentName;

    @Column(name = "chunk_hash", nullable = false, length = 64)
    private String chunkHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
