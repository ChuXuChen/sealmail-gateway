package com.sealmail.app.dto.response;

import com.sealmail.domain.mailauth.DnsProbeResult;

import java.time.Instant;

public record MailAuthDnsProbeResponse(
        String domainName,
        String recordType,
        String expectedName,
        String expectedValueHash,
        String observedValue,
        String status,
        String detail,
        Instant checkedAt
) {

    public static MailAuthDnsProbeResponse from(DnsProbeResult result) {
        return new MailAuthDnsProbeResponse(
                result.domainName(),
                result.recordType(),
                result.expectedName(),
                result.expectedValueHash(),
                result.observedValue(),
                result.status().name(),
                result.detail(),
                result.checkedAt());
    }
}
