package com.sealmail.domain.mailauth;

import java.time.Instant;

public record DnsProbeResult(
        String domainName,
        String recordType,
        String expectedName,
        String expectedValueHash,
        String observedValue,
        DnsProbeStatus status,
        String detail,
        Instant checkedAt
) {

    public DnsProbeResult {
        if (domainName == null || domainName.isBlank()) {
            throw new IllegalArgumentException("DNS probe domain cannot be blank");
        }
        if (recordType == null || recordType.isBlank()) {
            throw new IllegalArgumentException("DNS probe record type cannot be blank");
        }
        if (expectedName == null || expectedName.isBlank()) {
            throw new IllegalArgumentException("DNS probe expected name cannot be blank");
        }
        status = status != null ? status : DnsProbeStatus.ERROR;
        checkedAt = checkedAt != null ? checkedAt : Instant.now();
    }
}
