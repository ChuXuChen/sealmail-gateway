package com.sealmail.app.dto.response;

public record DnsRecordResponse(
        String type,
        String name,
        String value,
        boolean available
) {
}
