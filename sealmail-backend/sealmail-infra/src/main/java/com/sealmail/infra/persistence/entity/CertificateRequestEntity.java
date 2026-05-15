package com.sealmail.infra.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "certificate_request",
        indexes = {
                @Index(name = "idx_certreq_status", columnList = "status"),
                @Index(name = "idx_certreq_submitted_at", columnList = "submitted_at")
        })
public class CertificateRequestEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "csr_pem", nullable = false, columnDefinition = "TEXT")
    private String csrPem;

    @Column(name = "requested_owner_email", length = 254)
    private String requestedOwnerEmail;

    @Column(name = "submitted_by", length = 128)
    private String submittedBy;

    @Column(name = "submitter_ip", length = 64)
    private String submitterIp;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by", length = 128)
    private String decidedBy;

    @Column(name = "decision_comment", length = 512)
    private String decisionComment;

    @Column(name = "issued_cert_id", length = 128)
    private String issuedCertId;

    @Column(name = "intermediate_ca_id", length = 128)
    private String intermediateCaId;

    @Version
    @Column(name = "version")
    private long version;
}
