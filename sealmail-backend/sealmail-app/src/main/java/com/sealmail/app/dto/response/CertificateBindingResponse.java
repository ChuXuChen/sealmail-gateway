package com.sealmail.app.dto.response;

import java.time.Instant;

public record CertificateBindingResponse(
        String id,
        String domain,
        String ownerEmail,
        String certificateId,
        String certificateAlias,
        String purpose,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
