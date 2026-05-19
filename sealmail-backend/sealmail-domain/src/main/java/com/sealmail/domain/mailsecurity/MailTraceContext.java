package com.sealmail.domain.mailsecurity;

public record MailTraceContext(
        String processingId,
        AuditTrace auditTrace
) {
}
