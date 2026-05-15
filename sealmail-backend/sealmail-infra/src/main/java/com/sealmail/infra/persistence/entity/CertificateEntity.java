package com.sealmail.infra.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Set;

@Data
@Entity
@Table(name = "certificate")
public class CertificateEntity {

    @Id
    @Column(name = "thumbprint", length = 128)
    private String thumbprint;

    @Column(name = "owner_email", nullable = false, length = 254)
    private String ownerEmail;

    @Column(name = "pem_content", nullable = false, columnDefinition = "TEXT")
    private String pemContent;

    @Column(name = "alias", length = 255)
    private String alias;

    @Column(name = "not_before", nullable = false)
    private Instant notBefore;

    @Column(name = "not_after", nullable = false)
    private Instant notAfter;

    @Column(name = "key_usages", length = 1024)
    private String keyUsages;

    @Column(name = "issuer_dn", length = 1024)
    private String issuerDn;

    @Column(name = "subject_dn", length = 1024)
    private String subjectDn;

    @Column(name = "serial_number", length = 128)
    private String serialNumber;

    @Column(name = "subject_key_id", length = 128)
    private String subjectKeyIdentifier;

    @Column(name = "private_key_data", columnDefinition = "TEXT")
    private String privateKeyData;

    @Column(name = "has_private_key", nullable = false)
    private boolean hasPrivateKey;

    @Column(name = "algorithm", length = 32)
    private String algorithm;

    @Column(name = "trusted", nullable = false)
    private boolean trusted;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "revocation_reason", length = 512)
    private String revocationReason;

    @Column(name = "revocation_date")
    private Instant revocationDate;

    @Column(name = "revocation_crl_reason", length = 32)
    private String revocationCrlReason;

    @Column(name = "is_ca", nullable = false)
    private boolean ca;

    /** -1 = no constraint; null = no BasicConstraints set (i.e. end-entity) */
    @Column(name = "path_len_constraint")
    private Integer pathLenConstraint;

    /** Thumbprint of the issuing certificate in our store; null for self-signed roots. */
    @Column(name = "issuer_cert_id", length = 128)
    private String issuerCertId;

    @Column(name = "extended_key_usages", length = 1024)
    private String extendedKeyUsages;

    @Column(name = "crl_dp_url", length = 1024)
    private String crlDpUrl;

    @Column(name = "imported_crl_pem", columnDefinition = "TEXT")
    private String importedCrlPem;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private long version;
}
