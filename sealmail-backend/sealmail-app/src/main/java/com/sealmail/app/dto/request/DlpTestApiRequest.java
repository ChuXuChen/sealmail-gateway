package com.sealmail.app.dto.request;

import java.util.List;

public record DlpTestApiRequest(
        String subject,
        String body,
        String sender,
        List<String> recipients,
        String direction
) {
    public DlpTestApiRequest {
        recipients = recipients == null ? null : List.copyOf(recipients);
    }
}
