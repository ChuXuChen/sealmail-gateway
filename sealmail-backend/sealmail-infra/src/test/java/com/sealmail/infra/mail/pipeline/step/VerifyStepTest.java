package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.mailsecurity.CertificateSelection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.mail.pipeline.PipelineResult;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerifyStepTest {

    @Test
    void extractsOriginalContentAfterSuccessfulSignatureVerification() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        VerifyStep verifyStep = new VerifyStep(smimeOperations);
        byte[] signedPayload = "signed".getBytes();
        byte[] extractedPayload = "plain".getBytes();

        when(smimeOperations.isSigned(signedPayload)).thenReturn(true);
        when(smimeOperations.verifySignature(signedPayload, "sender-cert")).thenReturn(true);
        when(smimeOperations.extractSignedContent(signedPayload)).thenReturn(extractedPayload);

        PipelineResult result = verifyStep.execute(message(signedPayload));

        assertTrue(result.success());
        assertArrayEquals(extractedPayload, result.payload());
        verify(smimeOperations).isSigned(signedPayload);
        verify(smimeOperations).verifySignature(signedPayload, "sender-cert");
        verify(smimeOperations).extractSignedContent(signedPayload);
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
                .withCertificateSelection(CertificateSelection.empty()
                        .withSenderCertificate("sender-cert", null));
        return MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }
}
