package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.infra.config.properties.RelayProperties;
import com.sealmail.infra.mail.relay.SmtpRelayClient;
import com.sealmail.infra.mail.relay.SmtpRelayConnectionSettings;
import com.sealmail.infra.mail.relay.SmtpRelayRequest;
import com.sealmail.infra.mail.pipeline.MailPipelineStep;
import com.sealmail.infra.mail.pipeline.PipelineResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pipeline step: Relay processed mail to downstream SMTP server.
 */
@Component
public class RelayStep implements MailPipelineStep {

    private static final Logger log = LoggerFactory.getLogger(RelayStep.class);

    private final RelayProperties relayProperties;
    private final SmtpRelayClient smtpRelayClient;

    public RelayStep(RelayProperties relayProperties, SmtpRelayClient smtpRelayClient) {
        this.relayProperties = relayProperties;
        this.smtpRelayClient = smtpRelayClient;
    }

    @Override
    public PipelineResult execute(Message<byte[]> message) {
        MailEnvelope envelope = (MailEnvelope) message.getHeaders().get("mailEnvelope");
        if (envelope == null) {
            return PipelineResult.failure("Mail envelope not found in message headers");
        }

        byte[] mailContent = message.getPayload();
        if (mailContent == null || mailContent.length == 0) {
            return PipelineResult.failure("Mail content is empty");
        }

        // Read from message headers or fall back to properties
        String host = readStringHeader(message, "relayHost", relayProperties.getHost());
        int port = readIntHeader(message, "relayPort", relayProperties.getPort());
        String username = readStringHeader(message, "relayUsername", relayProperties.getUsername());
        String password = readStringHeader(message, "relayPassword", relayProperties.getPassword());
        boolean useTls = readBooleanHeader(message, "relayUseTls", relayProperties.isUseTls());
        int timeout = readIntHeader(message, "relayTimeout", relayProperties.getTimeout());
        try {
            String envelopeFrom = readStringHeader(message, "relayEnvelopeFrom", null);
            if (!hasText(envelopeFrom)) {
                envelopeFrom = hasText(username) ? username : envelope.getSender().getValue();
            }
            List<String> recipients = envelope.getRecipients().stream()
                    .map(addr -> addr.getValue())
                    .toList();
            SmtpRelayConnectionSettings connection = new SmtpRelayConnectionSettings(
                    host,
                    port,
                    useTls,
                    username,
                    password,
                    timeout
            );

            log.info("=== RELAYING TO: {}:{} mode={} user: {} ===",
                    host, port, connection.useImplicitTls() ? "SMTPS" : (connection.useStartTls() ? "STARTTLS" : "PLAIN"), username);
            smtpRelayClient.send(new SmtpRelayRequest(connection, envelopeFrom, recipients, mailContent));

            log.info("=== Mail successfully relayed ===");
            log.info("  Relay Host: {}:{}", host, port);
            log.info("  Envelope From: {}", envelopeFrom);
            log.info("  To: {}", envelope.getRecipients());
            log.info("  Size: {} bytes", mailContent.length);
            log.info("=================================");

            return PipelineResult.success(mailContent);

        } catch (Exception e) {
            log.error("Relay step failed: {} - host: {}, port: {}, user: {}",
                    e.getMessage(), host, port, username, e);
            return PipelineResult.failure("Mail relay failed: " + e.getMessage());
        }
    }

    @Override
    public String getStepName() {
        return "relay";
    }

    private static String readStringHeader(Message<byte[]> message, String headerName, String fallback) {
        Object value = message.getHeaders().get(headerName);
        return value instanceof String stringValue ? stringValue : fallback;
    }

    private static int readIntHeader(Message<byte[]> message, String headerName, int fallback) {
        Object value = message.getHeaders().get(headerName);
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Integer.parseInt(stringValue);
        }
        return fallback;
    }

    private static boolean readBooleanHeader(Message<byte[]> message, String headerName, boolean fallback) {
        Object value = message.getHeaders().get(headerName);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Boolean.parseBoolean(stringValue);
        }
        return fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
