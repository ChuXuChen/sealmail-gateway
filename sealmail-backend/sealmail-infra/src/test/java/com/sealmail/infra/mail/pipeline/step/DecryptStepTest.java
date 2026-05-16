package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.crypto.KeyStoreService;
import com.sealmail.infra.mail.pipeline.PipelineResult;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                mock(CertificateRepository.class)
        );
        byte[] payload = "plain-text".getBytes();
        when(smimeOperations.isEncrypted(payload)).thenReturn(false);

        PipelineResult result = decryptStep.execute(message(payload));

        assertTrue(result.success());
        assertArrayEquals(payload, result.payload());
        verify(smimeOperations).isEncrypted(payload);
        verifyNoMoreInteractions(smimeOperations);
    }

    @Test
    void quarantinesEncryptedMailWithoutDecryptionMaterial() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        DecryptStep decryptStep = new DecryptStep(
                smimeOperations,
                mock(KeyStoreService.class),
                mock(CertificateRepository.class)
        );
        byte[] payload = "cipher-text".getBytes();
        when(smimeOperations.isEncrypted(payload)).thenReturn(true);

        PipelineResult result = decryptStep.execute(message(payload));

        assertFalse(result.success());
        assertTrue(result.requiresQuarantine());
        assertEquals("DECRYPTION_FAILED", result.quarantineReason());
        assertArrayEquals(payload, result.payload());
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
