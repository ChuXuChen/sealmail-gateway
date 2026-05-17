package com.sealmail.domain.dlp;

import com.sealmail.domain.mailsecurity.MailDirection;

import java.util.List;

public record DlpTestRequest(
        String subject,
        String body,
        String sender,
        List<String> recipients,
        MailDirection direction
) {
    public DlpTestRequest {
        recipients = recipients == null ? List.of() : List.copyOf(recipients);
    }
}
