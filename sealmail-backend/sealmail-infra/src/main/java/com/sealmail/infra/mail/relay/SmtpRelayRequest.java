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
        envelopeFrom = SmtpEnvelopeAddress.requireValid(envelopeFrom, "SMTP envelope sender");
        if (recipients == null || recipients.isEmpty()) {
            throw new IllegalArgumentException("SMTP recipients must not be empty");
        }
        recipients = recipients.stream()
                .map(recipient -> SmtpEnvelopeAddress.requireValid(recipient, "SMTP recipient"))
                .toList();
        if (messageData == null || messageData.length == 0) {
            throw new IllegalArgumentException("SMTP message payload must not be empty");
        }
        messageData = messageData.clone();
    }
}
