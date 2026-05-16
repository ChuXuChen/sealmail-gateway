package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.crypto.KeyStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;
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
                mock(KeyStoreService.class),
                mock(CertificateRepository.class),
                mock(DomainEventPublisher.class)
        );
        byte[] payload = "plain-text".getBytes();
        when(smimeOperations.isEncrypted(payload)).thenReturn(false);

        Message<byte[]> result = decryptStep.execute(message(payload));

        assertArrayEquals(payload, result.getPayload());
        verify(smimeOperations).isEncrypted(payload);
        verifyNoMoreInteractions(smimeOperations);
    }

    @Test
    void encryptedMailWithoutDecryptionMaterialEntersUnifiedErrorFlow() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        DecryptStep decryptStep = new DecryptStep(
                smimeOperations,
                mock(KeyStoreService.class),
                mock(CertificateRepository.class),
                mock(DomainEventPublisher.class)
        );
        byte[] payload = "cipher-text".getBytes();
        when(smimeOperations.isEncrypted(payload)).thenReturn(true);

        MailProcessingException error = assertThrows(
                MailProcessingException.class,
                () -> decryptStep.execute(message(payload)));

        assertEquals(MailProcessingErrorType.DECRYPTION, error.errorType());
        assertTrue(error.getMessage().contains("missing recipient certificate/private key"));
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
        return MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, MailProcessingContext.create(envelope))
                .build();
    }

}
