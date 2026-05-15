package com.sealmail.infra.mail.relay;

import java.util.List;

/**
 * Full SMTP relay request with envelope and RFC822 payload.
 */
public record SmtpRelayRequest(
        SmtpRelayConnectionSettings connection,
        String envelopeFrom,
        List<String> recipients,
        byte[] messageData
) {

    public SmtpRelayRequest {
        if (connection == null) {
            throw new IllegalArgumentException("SMTP relay connection settings must not be null");
        }
        if (envelopeFrom == null || envelopeFrom.isBlank()) {
            throw new IllegalArgumentException("SMTP envelope sender must not be blank");
        }
        if (recipients == null || recipients.isEmpty()) {
            throw new IllegalArgumentException("SMTP recipients must not be empty");
        }
        recipients = List.copyOf(recipients);
        if (messageData == null || messageData.length == 0) {
            throw new IllegalArgumentException("SMTP message payload must not be empty");
        }
        messageData = messageData.clone();
    }
}
