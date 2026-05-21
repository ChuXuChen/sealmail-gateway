package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.mailsecurity.CertificateSelection;
import com.sealmail.domain.mailsecurity.CryptoProfile;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingDecision;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.event.MailEncrypted;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EncryptStepTest {

    @Test
    void encryptsAllRecipientsInOneSmimeEnvelope() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, domainEventPublisher, null);

        EmailAddress sender = new EmailAddress("sender@example.com");
        EmailAddress recipientA = new EmailAddress("a@example.com");
        EmailAddress recipientB = new EmailAddress("b@example.com");
        byte[] payload = "hello".getBytes();
        byte[] encrypted = "encrypted".getBytes();
        when(smimeOperations.encryptMultiple(same(payload), any(), eq(CryptoProfile.STANDARD)))
                .thenReturn(encrypted);

        Message<byte[]> message = message(payload, sender, List.of(recipientA, recipientB),
                MailProcessingDecision.none().withEncryptionRequired(true),
                CryptoProfile.STANDARD,
                certificates(
                        Map.entry(recipientA, "cert-A"),
                        Map.entry(recipientB, "cert-B")));

        Message<byte[]> result = encryptStep.execute(message);

        assertArrayEquals(encrypted, result.getPayload());
        verify(domainEventPublisher, times(2)).publishEvent(any(MailEncrypted.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> certCaptor = ArgumentCaptor.forClass(List.class);
        verify(smimeOperations).encryptMultiple(
                same(payload),
                certCaptor.capture(),
                eq(CryptoProfile.STANDARD));
        assertEquals(2, certCaptor.getValue().size());
        assertTrue(certCaptor.getValue().contains("cert-A"));
        assertTrue(certCaptor.getValue().contains("cert-B"));
        verify(smimeOperations, never()).encrypt(any(), anyString());
    }

    @Test
    void mustEncryptQuarantinesWhenNoRecipientCertificateExists() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, mock(DomainEventPublisher.class), null);

        byte[] payload = "hello".getBytes();
        Message<byte[]> message = message(payload, new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("a@example.com")),
                MailProcessingDecision.none().withMustEncrypt(true),
                CryptoProfile.STANDARD);

        MailProcessingException error = assertThrows(MailProcessingException.class, () -> encryptStep.execute(message));

        assertEquals(MailProcessingErrorType.ENCRYPTION, error.errorType());
        assertEquals("DLP MUST_ENCRYPT: 未找到收件人加密证书", error.getMessage());
        verify(smimeOperations, never()).encryptMultiple(any(), any(), any(CryptoProfile.class));
    }

    @Test
    void mustEncryptFailureIsRecordedAsExceptionMail() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, mock(DomainEventPublisher.class), null);

        byte[] payload = "hello".getBytes();
        Message<byte[]> message = message(payload, new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("a@example.com")),
                MailProcessingDecision.none().withMustEncrypt(true),
                CryptoProfile.STANDARD);

        MailProcessingException error = assertThrows(MailProcessingException.class, () -> encryptStep.execute(message));

        assertEquals(MailProcessingErrorType.ENCRYPTION, error.errorType());
    }

    @Test
    void mustEncryptUsesCertificatesSelectedByRoutingEntry() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, mock(DomainEventPublisher.class), null);

        EmailAddress recipient = new EmailAddress("a@example.com");
        byte[] payload = "hello".getBytes();
        byte[] encrypted = "encrypted".getBytes();
        when(smimeOperations.encryptMultiple(same(payload), any(), eq(CryptoProfile.STANDARD)))
                .thenReturn(encrypted);

        Message<byte[]> message = message(payload, new EmailAddress("sender@example.com"),
                List.of(recipient),
                MailProcessingDecision.none().withMustEncrypt(true),
                CryptoProfile.STANDARD,
                certificates(Map.entry(recipient, "cert-A")));

        Message<byte[]> result = encryptStep.execute(message);

        assertArrayEquals(encrypted, result.getPayload());
        verify(smimeOperations).encryptMultiple(same(payload), any(), eq(CryptoProfile.STANDARD));
    }

    @Test
    void mustEncryptQuarantinesWhenAnyRecipientCertificateIsMissing() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, mock(DomainEventPublisher.class), null);

        EmailAddress recipientA = new EmailAddress("a@example.com");
        EmailAddress recipientB = new EmailAddress("b@example.com");
        byte[] payload = "hello".getBytes();
        Message<byte[]> message = message(payload, new EmailAddress("sender@example.com"),
                List.of(recipientA, recipientB),
                MailProcessingDecision.none().withMustEncrypt(true),
                CryptoProfile.STANDARD,
                certificates(Map.entry(recipientA, "cert-A")));

        MailProcessingException error = assertThrows(MailProcessingException.class, () -> encryptStep.execute(message));

        assertTrue(error.getMessage().contains("b@example.com"));
        verify(smimeOperations, never()).encryptMultiple(any(), any(), any(CryptoProfile.class));
    }

    @Test
    void failsWhenRoutingEntryCouldNotDetermineSharedCryptoProfile() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, mock(DomainEventPublisher.class), null);

        EmailAddress recipientA = new EmailAddress("a@example.com");
        EmailAddress recipientB = new EmailAddress("b@example.com");
        byte[] payload = "hello".getBytes();
        Message<byte[]> message = message(payload, new EmailAddress("sender@example.com"),
                List.of(recipientA, recipientB),
                MailProcessingDecision.none().withEncryptionRequired(true),
                CryptoProfile.AUTO,
                certificates(
                        Map.entry(recipientA, "cert-A"),
                        Map.entry(recipientB, "cert-B")));

        MailProcessingException error = assertThrows(MailProcessingException.class, () -> encryptStep.execute(message));

        assertTrue(error.getMessage().contains("无法确定邮件加密Profile"));
        verify(smimeOperations, never()).encryptMultiple(any(), any(), any(CryptoProfile.class));
    }

    @Test
    void gmOnlyUsesGmSuiteWhenAllRecipientsHaveSm2Certificates() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, mock(DomainEventPublisher.class), null);

        EmailAddress recipientA = new EmailAddress("a@example.com");
        EmailAddress recipientB = new EmailAddress("b@example.com");
        byte[] payload = "hello".getBytes();
        byte[] encrypted = "encrypted".getBytes();
        when(smimeOperations.encryptMultiple(same(payload), any(), eq(CryptoProfile.GM)))
                .thenReturn(encrypted);

        Message<byte[]> message = message(payload, new EmailAddress("sender@example.com"),
                List.of(recipientA, recipientB),
                MailProcessingDecision.none().withEncryptionRequired(true),
                CryptoProfile.GM,
                certificates(
                        Map.entry(recipientA, "cert-A"),
                        Map.entry(recipientB, "cert-B")));

        Message<byte[]> result = encryptStep.execute(message);

        assertArrayEquals(encrypted, result.getPayload());
        verify(smimeOperations).encryptMultiple(same(payload), any(), eq(CryptoProfile.GM));
    }

    @Test
    void encryptsWhenOnlyContextRequiresEncryption() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, mock(DomainEventPublisher.class), null);

        EmailAddress recipient = new EmailAddress("a@example.com");
        byte[] payload = "hello".getBytes();
        byte[] encrypted = "encrypted".getBytes();
        when(smimeOperations.encryptMultiple(same(payload), any(), eq(CryptoProfile.STANDARD)))
                .thenReturn(encrypted);

        MailEnvelope envelope = new MailEnvelope(
                "msg-" + UUID.randomUUID() + "@example.com",
                new EmailAddress("sender@example.com"),
                List.of(recipient),
                "127.0.0.1",
                "helo",
                Instant.now(),
                payload
        );
        MailProcessingContext context = MailProcessingContext.create(envelope)
                .withDecision(MailProcessingDecision.none().withEncryptionRequired(true))
                .withCryptoProfile(CryptoProfile.STANDARD)
                .withCertificateSelection(certificates(Map.entry(recipient, "cert-A")));
        Message<byte[]> message = MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();

        Message<byte[]> result = encryptStep.execute(message);

        assertArrayEquals(encrypted, result.getPayload());
        verify(smimeOperations).encryptMultiple(same(payload), any(), eq(CryptoProfile.STANDARD));
    }

    @SafeVarargs
    private static CertificateSelection certificates(Map.Entry<EmailAddress, String>... entries) {
        java.util.Map<EmailAddress, String> certificates = new java.util.LinkedHashMap<>();
        java.util.Map<EmailAddress, String> thumbprints = new java.util.LinkedHashMap<>();
        for (Map.Entry<EmailAddress, String> entry : entries) {
            certificates.put(entry.getKey(), entry.getValue());
            thumbprints.put(entry.getKey(), "thumbprint-" + entry.getKey().getValue());
        }
        return CertificateSelection.empty().withRecipientCertificates(certificates, thumbprints);
    }

    private static Message<byte[]> message(byte[] payload,
                                           EmailAddress sender,
                                           List<EmailAddress> recipients,
                                           MailProcessingDecision decision,
                                           CryptoProfile cryptoProfile) {
        return message(payload, sender, recipients, decision, cryptoProfile, CertificateSelection.empty());
    }

    private static Message<byte[]> message(byte[] payload,
                                           EmailAddress sender,
                                           List<EmailAddress> recipients,
                                           MailProcessingDecision decision,
                                           CryptoProfile cryptoProfile,
                                           CertificateSelection certificates) {
        MailEnvelope envelope = new MailEnvelope(
                "msg-" + UUID.randomUUID() + "@example.com",
                sender,
                recipients,
                "127.0.0.1",
                "helo",
                Instant.now(),
                payload
        );
        MailProcessingContext context = MailProcessingContext.create(envelope)
                .withDecision(decision)
                .withCryptoProfile(cryptoProfile)
                .withCertificateSelection(certificates);
        return MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }

    private static MailProcessingContext context(Message<?> message) {
        return (MailProcessingContext) message.getHeaders().get(MailProcessingHeaders.CONTEXT);
    }
}
