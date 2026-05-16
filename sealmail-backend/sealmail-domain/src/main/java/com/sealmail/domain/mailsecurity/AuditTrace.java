package com.sealmail.domain.mailsecurity;

public record AuditTrace(
        String processingId,
        String correlationId,
        String messageId,
        String submissionType,
        String remoteAddress
) {
}
