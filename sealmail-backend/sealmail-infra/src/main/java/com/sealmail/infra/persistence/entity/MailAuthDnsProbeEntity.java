package com.sealmail.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "mail_auth_dns_probe")
public class MailAuthDnsProbeEntity {

    @Id
    @Column(name = "id", length = 128)
    private String id;

    @Column(name = "domain_name", nullable = false, length = 253)
    private String domainName;

    @Column(name = "record_type", nullable = false, length = 32)
    private String recordType;

    @Column(name = "expected_name", nullable = false, length = 512)
    private String expectedName;

    @Column(name = "expected_value_hash", length = 128)
    private String expectedValueHash;

    @Column(name = "observed_value", columnDefinition = "TEXT")
    private String observedValue;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "detail", length = 1024)
    private String detail;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;
}
