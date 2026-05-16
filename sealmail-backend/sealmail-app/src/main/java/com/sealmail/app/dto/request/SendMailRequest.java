package com.sealmail.app.dto.request;

import java.util.List;

public record SendMailRequest(
        String from,
        List<String> to,
        String subject,
        String content,
        String preferredAlgorithm
) {
    public SendMailRequest {
        to = to == null ? List.of() : List.copyOf(to);
    }
}
