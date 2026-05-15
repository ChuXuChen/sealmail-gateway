package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.config.properties.RelayProperties;
import com.sealmail.infra.mail.relay.SmtpRelayClient;
import com.sealmail.infra.mail.relay.SmtpRelayRequest;
import com.sealmail.infra.mail.pipeline.PipelineResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RelayStepTest {

    @Test
    void usesAuthenticatedRelayUserAsEnvelopeSender() throws Exception {
        RelayProperties relayProperties = new RelayProperties();
        relayProperties.setHost("fallback.example.com");
        relayProperties.setPort(465);
        relayProperties.setUsername("fallback@example.com");
        relayProperties.setPassword("fallback-secret");
        relayProperties.setUseTls(true);
        relayProperties.setTimeout(30000);

        SmtpRelayClient smtpRelayClient = mock(SmtpRelayClient.class);
        doNothing().when(smtpRelayClient).send(org.mockito.ArgumentMatchers.any(SmtpRelayRequest.class));
        RelayStep relayStep = new RelayStep(relayProperties, smtpRelayClient);

        byte[] payload = "Subject: Test\r\n\r\nBody".getBytes();
        Message<byte[]> message = MessageBuilder.withPayload(payload)
                .setHeader("mailEnvelope", envelope("sender@example.com", List.of("a@example.com", "b@example.com"), payload))
                .setHeader("relayHost", "smtp.override.example.com")
                .setHeader("relayPort", "587")
                .setHeader("relayUsername", "auth@example.com")
                .setHeader("relayPassword", "auth-secret")
                .setHeader("relayUseTls", "true")
                .setHeader("relayTimeout", "12000")
                .build();

        PipelineResult result = relayStep.execute(message);

        assertTrue(result.success());
        ArgumentCaptor<SmtpRelayRequest> requestCaptor = ArgumentCaptor.forClass(SmtpRelayRequest.class);
        verify(smtpRelayClient).send(requestCaptor.capture());

        SmtpRelayRequest request = requestCaptor.getValue();
        assertEquals("smtp.override.example.com", request.connection().host());
        assertEquals(587, request.connection().port());
        assertTrue(request.connection().useStartTls());
        assertEquals("auth@example.com", request.envelopeFrom());
        assertEquals(List.of("a@example.com", "b@example.com"), request.recipients());
        assertArrayEquals(payload, request.messageData());
    }

    @Test
    void fallsBackToOriginalSenderWhenRelayAuthIsDisabled() throws Exception {
        RelayProperties relayProperties = new RelayProperties();
        relayProperties.setHost("smtp.example.com");
        relayProperties.setPort(25);
        relayProperties.setUsername("");
        relayProperties.setPassword("");
        relayProperties.setUseTls(false);
        relayProperties.setTimeout(5000);

        SmtpRelayClient smtpRelayClient = mock(SmtpRelayClient.class);
        doNothing().when(smtpRelayClient).send(org.mockito.ArgumentMatchers.any(SmtpRelayRequest.class));
        RelayStep relayStep = new RelayStep(relayProperties, smtpRelayClient);

        byte[] payload = "Subject: Test\r\n\r\nBody".getBytes();
        Message<byte[]> message = MessageBuilder.withPayload(payload)
                .setHeader("mailEnvelope", envelope("sender@example.com", List.of("recipient@example.com"), payload))
                .build();

        PipelineResult result = relayStep.execute(message);

        assertTrue(result.success());
        ArgumentCaptor<SmtpRelayRequest> requestCaptor = ArgumentCaptor.forClass(SmtpRelayRequest.class);
        verify(smtpRelayClient).send(requestCaptor.capture());
        assertEquals("sender@example.com", requestCaptor.getValue().envelopeFrom());
    }

    @Test
    void prefersExplicitRelayEnvelopeFromOverride() throws Exception {
        RelayProperties relayProperties = new RelayProperties();
        relayProperties.setHost("smtp.example.com");
        relayProperties.setPort(25);
        relayProperties.setUsername("");
        relayProperties.setPassword("");
        relayProperties.setUseTls(false);
        relayProperties.setTimeout(5000);

        SmtpRelayClient smtpRelayClient = mock(SmtpRelayClient.class);
        doNothing().when(smtpRelayClient).send(org.mockito.ArgumentMatchers.any(SmtpRelayRequest.class));
        RelayStep relayStep = new RelayStep(relayProperties, smtpRelayClient);

        byte[] payload = "Subject: Test\r\n\r\nBody".getBytes();
        Message<byte[]> message = MessageBuilder.withPayload(payload)
                .setHeader("mailEnvelope", envelope("sender@example.com", List.of("recipient@example.com"), payload))
                .setHeader("relayEnvelopeFrom", "override@example.com")
                .build();

        PipelineResult result = relayStep.execute(message);

        assertTrue(result.success());
        ArgumentCaptor<SmtpRelayRequest> requestCaptor = ArgumentCaptor.forClass(SmtpRelayRequest.class);
        verify(smtpRelayClient).send(requestCaptor.capture());
        assertEquals("override@example.com", requestCaptor.getValue().envelopeFrom());
    }

    private static MailEnvelope envelope(String sender, List<String> recipients, byte[] payload) {
        return new MailEnvelope(
                "msg-" + UUID.randomUUID() + "@example.com",
                new EmailAddress(sender),
                recipients.stream().map(EmailAddress::new).toList(),
                "127.0.0.1",
                "helo",
                Instant.now(),
                payload
        );
    }
}
