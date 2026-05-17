package com.sealmail.domain.mailauth;

public record DnsRecord(
        String type,
        String name,
        String value,
        boolean available
) {

    public DnsRecord {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("DNS record type cannot be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("DNS record name cannot be blank");
        }
        value = value != null ? value : "";
    }
}
