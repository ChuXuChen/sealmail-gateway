package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.KeyUsage;
import com.sealmail.domain.certificate.ValidityPeriod;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingDecision;
import com.sealmail.domain.mailsecurity.RelayProfile;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.config.properties.RelayProperties;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.mail.relay.SmtpRelayClient;
import com.sealmail.infra.mail.relay.SmtpRelayRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        RelayStep relayStep = new RelayStep(relayProperties, smtpRelayClient, mock(CertificateRepository.class));

        byte[] payload = "Subject: Test\r\n\r\nBody".getBytes();
        Message<byte[]> message = message(
                payload,
                "sender@example.com",
                List.of("a@example.com", "b@example.com"),
                MailDirection.OUTBOUND,
                MailProcessingDecision.none(),
                new RelayProfile("smtp.override.example.com", 587, true,
                        "auth@example.com", "auth-secret", 12000, null));

        Message<byte[]> result = relayStep.execute(message);

        assertArrayEquals(payload, result.getPayload());
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
        RelayStep relayStep = new RelayStep(relayProperties, smtpRelayClient, mock(CertificateRepository.class));

        byte[] payload = "Subject: Test\r\n\r\nBody".getBytes();
        Message<byte[]> message = message(payload, "sender@example.com", List.of("recipient@example.com"),
                MailDirection.OUTBOUND, MailProcessingDecision.none(), null);

        Message<byte[]> result = relayStep.execute(message);

        assertArrayEquals(payload, result.getPayload());
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
        RelayStep relayStep = new RelayStep(relayProperties, smtpRelayClient, mock(CertificateRepository.class));

        byte[] payload = "Subject: Test\r\n\r\nBody".getBytes();
        Message<byte[]> message = message(payload, "sender@example.com", List.of("recipient@example.com"),
                MailDirection.OUTBOUND, MailProcessingDecision.none(),
                new RelayProfile("smtp.example.com", 25, false, "", "", 5000, "override@example.com"));

        Message<byte[]> result = relayStep.execute(message);

        assertArrayEquals(payload, result.getPayload());
        ArgumentCaptor<SmtpRelayRequest> requestCaptor = ArgumentCaptor.forClass(SmtpRelayRequest.class);
        verify(smtpRelayClient).send(requestCaptor.capture());
        assertEquals("override@example.com", requestCaptor.getValue().envelopeFrom());
    }

    @Test
    void usesRelayProfileFromContext() throws Exception {
        RelayProperties relayProperties = new RelayProperties();
        relayProperties.setHost("fallback.example.com");
        relayProperties.setPort(25);
        relayProperties.setUsername("");
        relayProperties.setPassword("");
        relayProperties.setUseTls(false);
        relayProperties.setTimeout(5000);

        SmtpRelayClient smtpRelayClient = mock(SmtpRelayClient.class);
        doNothing().when(smtpRelayClient).send(org.mockito.ArgumentMatchers.any(SmtpRelayRequest.class));
        RelayStep relayStep = new RelayStep(relayProperties, smtpRelayClient, mock(CertificateRepository.class));

        byte[] payload = "Subject: Test\r\n\r\nBody".getBytes();
        MailEnvelope envelope = envelope("sender@example.com", List.of("recipient@example.com"), payload);
        MailProcessingContext context = MailProcessingContext.create(envelope)
                .withRelayProfile(new RelayProfile(
                        "context.smtp.example.com",
                        2525,
                        true,
                        "context-user@example.com",
                        "secret",
                        12000,
                        "context-envelope@example.com"));
        Message<byte[]> message = MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();

        Message<byte[]> result = relayStep.execute(message);

        assertArrayEquals(payload, result.getPayload());
        ArgumentCaptor<SmtpRelayRequest> requestCaptor = ArgumentCaptor.forClass(SmtpRelayRequest.class);
        verify(smtpRelayClient).send(requestCaptor.capture());
        assertEquals("context.smtp.example.com", requestCaptor.getValue().connection().host());
        assertEquals(2525, requestCaptor.getValue().connection().port());
        assertTrue(requestCaptor.getValue().connection().useStartTls());
        assertEquals("context-envelope@example.com", requestCaptor.getValue().envelopeFrom());
    }

    @Test
    void refusesEncryptedOutboundRelayWhenAnyRecipientHasNoCertificate() throws Exception {
        RelayProperties relayProperties = new RelayProperties();
        relayProperties.setHost("smtp.example.com");
        relayProperties.setPort(25);
        relayProperties.setUsername("");
        relayProperties.setPassword("");
        relayProperties.setUseTls(false);
        relayProperties.setTimeout(5000);

        SmtpRelayClient smtpRelayClient = mock(SmtpRelayClient.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        RelayStep relayStep = new RelayStep(relayProperties, smtpRelayClient, certificateRepository);

        byte[] payload = "Content-Type: application/pkcs7-mime\r\n\r\nencrypted".getBytes();
        EmailAddress certified = new EmailAddress("2416507029@qq.com");
        EmailAddress missing = new EmailAddress("1261017453@qq.com");
        when(certificateRepository.findTrustedForEncryption(certified))
                .thenReturn(List.of(certificate(certified, "RSA")));
        when(certificateRepository.findTrustedForEncryption(missing)).thenReturn(List.of());

        Message<byte[]> message = message(payload, "sender@example.com",
                List.of(certified.getValue(), missing.getValue()),
                MailDirection.OUTBOUND,
                MailProcessingDecision.none().withEncryptionRequired(true),
                null);

        Message<byte[]> result = relayStep.execute(message);

        MailProcessingContext context = context(result);
        assertTrue(context.decision().requiresQuarantine());
        assertTrue(context.decision().quarantine().detail().contains("1261017453@qq.com"));
        verify(smtpRelayClient, never()).send(org.mockito.ArgumentMatchers.any(SmtpRelayRequest.class));
    }

    @Test
    void refusesSmimePayloadEvenWhenEncryptionHeadersWereLost() throws Exception {
        RelayProperties relayProperties = new RelayProperties();
        relayProperties.setHost("smtp.example.com");
        relayProperties.setPort(25);
        relayProperties.setUsername("");
        relayProperties.setPassword("");
        relayProperties.setUseTls(false);
        relayProperties.setTimeout(5000);

        SmtpRelayClient smtpRelayClient = mock(SmtpRelayClient.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        RelayStep relayStep = new RelayStep(relayProperties, smtpRelayClient, certificateRepository);

        byte[] payload = """
                MIME-Version: 1.0\r
                Content-Type: application/pkcs7-mime; smime-type=enveloped-data; name=\"smime.p7m\"\r
                \r
                encrypted
                """.getBytes();
        EmailAddress certified = new EmailAddress("2416507029@qq.com");
        EmailAddress missing = new EmailAddress("1261017453@qq.com");
        when(certificateRepository.findTrustedForEncryption(certified))
                .thenReturn(List.of(certificate(certified, "RSA")));
        when(certificateRepository.findTrustedForEncryption(missing)).thenReturn(List.of());

        Message<byte[]> message = message(payload, "sender@example.com",
                List.of(certified.getValue(), missing.getValue()),
                MailDirection.OUTBOUND,
                MailProcessingDecision.none(),
                null);

        Message<byte[]> result = relayStep.execute(message);

        MailProcessingContext context = context(result);
        assertTrue(context.decision().requiresQuarantine());
        assertEquals("CERTIFICATE_MISSING", context.decision().quarantine().reason());
        assertTrue(context.decision().quarantine().detail().contains("1261017453@qq.com"));
        verify(smtpRelayClient, never()).send(org.mockito.ArgumentMatchers.any(SmtpRelayRequest.class));
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

    private static Message<byte[]> message(byte[] payload,
                                           String sender,
                                           List<String> recipients,
                                           MailDirection direction,
                                           MailProcessingDecision decision,
                                           RelayProfile relayProfile) {
        MailProcessingContext context = MailProcessingContext.create(envelope(sender, recipients, payload))
                .withDirection(direction)
                .withDecision(decision)
                .withRelayProfile(relayProfile);
        return MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }

    private static Certificate certificate(EmailAddress owner, String algorithm) {
        Certificate cert = Certificate.importCertificate(
                new CertificateId(UUID.randomUUID().toString()),
                owner,
                "pem-" + UUID.randomUUID(),
                new ValidityPeriod(Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600)),
                Set.of(KeyUsage.ENCRYPTION),
                "CN=issuer",
                "CN=subject",
                BigInteger.ONE,
                "ski-" + UUID.randomUUID()
        );
        cert.trust();
        cert.setAlgorithm(algorithm);
        return cert;
    }

    private static MailProcessingContext context(Message<?> message) {
        return (MailProcessingContext) message.getHeaders().get(MailProcessingHeaders.CONTEXT);
    }
}
