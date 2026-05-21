package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.key.KeyManagementPort;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DecryptStepTest {

    @Test
    void skipsPlaintextMessages() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        DecryptStep decryptStep = new DecryptStep(
                smimeOperations,
                mock(KeyManagementPort.class),
                mock(DomainEventPublisher.class),
                null
        );
        byte[] payload = "plain-text".getBytes();
        when(smimeOperations.isEncrypted(payload)).thenReturn(false);

        Message<byte[]> result = decryptStep.execute(message(payload));

        assertArrayEquals(payload, result.getPayload());
        verify(smimeOperations).isEncrypted(payload);
        verifyNoMoreInteractions(smimeOperations);
    }

    @Test
    void skipsDecryptionWhenPassthroughHeaderIsSet() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        DecryptStep decryptStep = new DecryptStep(
                smimeOperations,
                mock(KeyManagementPort.class),
                mock(DomainEventPublisher.class),
                null
        );
        byte[] payload = "cipher-text".getBytes();

        Message<byte[]> result = decryptStep.execute(MessageBuilder.fromMessage(message(payload))
                .setHeader(MailProcessingHeaders.SKIP_DECRYPTION, true)
                .build());

        assertArrayEquals(payload, result.getPayload());
        verifyNoMoreInteractions(smimeOperations);
    }

    @Test
    void encryptedMailWithoutDecryptionMaterialEntersUnifiedErrorFlow() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        KeyManagementPort keyManagementPort = mock(KeyManagementPort.class);
        DecryptStep decryptStep = new DecryptStep(
                smimeOperations,
                keyManagementPort,
                mock(DomainEventPublisher.class),
                null
        );
        byte[] payload = "cipher-text".getBytes();
        when(smimeOperations.isEncrypted(payload)).thenReturn(true);
        when(keyManagementPort.findActiveKeyForCertificate("recipient-thumbprint")).thenReturn(Optional.empty());

        MailProcessingException error = assertThrows(
                MailProcessingException.class,
                () -> decryptStep.execute(message(payload)));

        assertEquals(MailProcessingErrorType.DECRYPTION, error.errorType());
        assertTrue(error.getMessage().contains("missing managed key"));
        verify(smimeOperations).isEncrypted(payload);
        verifyNoMoreInteractions(smimeOperations);
    }

    private static Message<byte[]> message(byte[] payload) {
        MailEnvelope envelope = new MailEnvelope(
                "msg-" + UUID.randomUUID() + "@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                "helo",
                Instant.now(),
                payload
        );
        MailProcessingContext context = MailProcessingContext.create(envelope)
                .withCertificateSelection(com.sealmail.domain.mailsecurity.CertificateSelection.empty()
                        .withRecipientCertificate("recipient-cert", "recipient-thumbprint"));
        return MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }

}
