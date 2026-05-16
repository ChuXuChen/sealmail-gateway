package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.infra.mail.auth.MailAuthenticationService;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.mail.pipeline.MailProcessingMessages;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class MailAuthenticationStep {

    private final MailAuthenticationService authenticationService;

    public MailAuthenticationStep(MailAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    public Message<byte[]> execute(Message<byte[]> message) {
        MailProcessingContext context = context(message);
        MailEnvelope envelope = context != null ? context.envelope() : null;
        var result = authenticate(message, envelope, context);
        if (result.shouldQuarantine()) {
            return MailProcessingMessages.quarantine(
                    message,
                    "EMAIL_AUTH_FAILED",
                    result.detail(),
                    MailRecordDisposition.EXCEPTION);
        }
        return MailProcessingMessages.withPayload(message, prependAuthenticationResults(message.getPayload(), result.header()));
    }

    public String getStepName() {
        return "mail-auth";
    }

    private byte[] prependAuthenticationResults(byte[] payload, String value) {
        if (value == null || value.isBlank()) {
            return payload;
        }
        byte[] header = ("Authentication-Results: " + value + "\r\n").getBytes(StandardCharsets.ISO_8859_1);
        byte[] combined = new byte[header.length + payload.length];
        System.arraycopy(header, 0, combined, 0, header.length);
        System.arraycopy(payload, 0, combined, header.length, payload.length);
        return combined;
    }

    private com.sealmail.infra.mail.auth.MailAuthenticationResult authenticate(Message<byte[]> message,
                                                                               MailEnvelope envelope,
                                                                               MailProcessingContext context) {
        try {
            return authenticationService.authenticate(message.getPayload(), envelope);
        } catch (Exception e) {
            throw new MailProcessingException(
                    MailProcessingErrorType.AUTHENTICATION,
                    "Mail authentication failed: " + e.getMessage(),
                    context,
                    e);
        }
    }

    private MailProcessingContext context(Message<?> message) {
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }
}
