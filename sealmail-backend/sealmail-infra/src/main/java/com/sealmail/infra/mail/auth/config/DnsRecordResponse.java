package com.sealmail.infra.mail.auth.config;

public record DnsRecordResponse(
        String type,
        String name,
        String value,
        boolean available
) {
}
