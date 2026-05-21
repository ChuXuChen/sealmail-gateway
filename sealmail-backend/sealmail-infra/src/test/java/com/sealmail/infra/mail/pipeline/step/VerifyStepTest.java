package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.mailsecurity.CertificateSelection;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class VerifyStepTest {

    @Test
    void extractsOriginalContentAfterSuccessfulSignatureVerification() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        VerifyStep verifyStep = new VerifyStep(smimeOperations, mock(DomainEventPublisher.class), null);
        byte[] signedPayload = "signed".getBytes();
        byte[] extractedPayload = "plain".getBytes();

        when(smimeOperations.isSigned(signedPayload)).thenReturn(true);
        when(smimeOperations.verifySignature(signedPayload, "sender-cert")).thenReturn(true);
        when(smimeOperations.extractSignedContent(signedPayload)).thenReturn(extractedPayload);

        Message<byte[]> result = verifyStep.execute(message(signedPayload));

        assertArrayEquals(extractedPayload, result.getPayload());
        verify(smimeOperations).isSigned(signedPayload);
        verify(smimeOperations).verifySignature(signedPayload, "sender-cert");
        verify(smimeOperations).extractSignedContent(signedPayload);
    }

    @Test
    void signedMailWithoutSenderCertificateEntersUnifiedErrorFlow() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        VerifyStep verifyStep = new VerifyStep(smimeOperations, mock(DomainEventPublisher.class), null);
        byte[] signedPayload = "signed".getBytes();

        when(smimeOperations.isSigned(signedPayload)).thenReturn(true);

        MailProcessingException error = assertThrows(
                MailProcessingException.class,
                () -> verifyStep.execute(message(signedPayload, CertificateSelection.empty())));

        assertEquals(MailProcessingErrorType.VERIFICATION, error.errorType());
        assertTrue(error.getMessage().contains("missing sender certificate"));
        verify(smimeOperations).isSigned(signedPayload);
        verifyNoMoreInteractions(smimeOperations);
    }

    @Test
    void invalidSignatureEntersUnifiedErrorFlow() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        VerifyStep verifyStep = new VerifyStep(smimeOperations, mock(DomainEventPublisher.class), null);
        byte[] signedPayload = "signed".getBytes();

        when(smimeOperations.isSigned(signedPayload)).thenReturn(true);
        when(smimeOperations.verifySignature(signedPayload, "sender-cert")).thenReturn(false);

        MailProcessingException error = assertThrows(
                MailProcessingException.class,
                () -> verifyStep.execute(message(signedPayload)));

        assertEquals(MailProcessingErrorType.VERIFICATION, error.errorType());
        assertTrue(error.getMessage().contains("Invalid S/MIME signature"));
        verify(smimeOperations).isSigned(signedPayload);
        verify(smimeOperations).verifySignature(signedPayload, "sender-cert");
        verifyNoMoreInteractions(smimeOperations);
    }

    private static Message<byte[]> message(byte[] payload) {
        return message(payload, CertificateSelection.empty()
                .withSenderCertificate("sender-cert", null));
    }

    private static Message<byte[]> message(byte[] payload, CertificateSelection certificateSelection) {
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
                .withCertificateSelection(certificateSelection);
        return MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }
}
