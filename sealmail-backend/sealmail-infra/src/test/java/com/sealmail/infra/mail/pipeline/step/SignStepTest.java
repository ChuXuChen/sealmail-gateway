package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.key.KeyManagementPort;
import com.sealmail.domain.mailsecurity.CertificateSelection;
import com.sealmail.domain.mailsecurity.CryptoProfile;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingDecision;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SignStepTest {

    @Test
    void selectedCertificateWithoutPrivateKeyEntersUnifiedErrorFlow() {
        KeyManagementPort keyManagementPort = mock(KeyManagementPort.class);
        SignStep signStep = new SignStep(
                keyManagementPort,
                mock(DomainEventPublisher.class));

        EmailAddress sender = new EmailAddress("sender@example.com");
        byte[] payload = "plain".getBytes();
        when(keyManagementPort.findActiveKeyForCertificate("sender-thumbprint")).thenReturn(Optional.empty());

        MailProcessingException error = assertThrows(
                MailProcessingException.class,
                () -> signStep.execute(message(payload, sender)));

        assertEquals(MailProcessingErrorType.SIGNING, error.errorType());
        assertTrue(error.getMessage().contains("已选择签名证书但未找到对应私钥"));
    }

    private static Message<byte[]> message(byte[] payload, EmailAddress sender) {
        MailEnvelope envelope = new MailEnvelope(
                "msg-" + UUID.randomUUID() + "@example.com",
                sender,
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                "helo",
                Instant.now(),
                payload
        );
        MailProcessingContext context = MailProcessingContext.create(envelope)
                .withDecision(MailProcessingDecision.none().withSigningRequired(true))
                .withCryptoProfile(CryptoProfile.STANDARD)
                .withCertificateSelection(CertificateSelection.empty()
                        .withSenderCertificate("sender-cert", "sender-thumbprint"));
        return MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }

}
