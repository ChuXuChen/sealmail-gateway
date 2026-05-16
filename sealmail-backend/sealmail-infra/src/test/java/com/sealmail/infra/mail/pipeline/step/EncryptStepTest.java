package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.KeyUsage;
import com.sealmail.domain.certificate.ValidityPeriod;
import com.sealmail.domain.certificate.spi.SMIMEEncryptionSuite;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingDecision;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.mailsecurity.event.MailEncrypted;
import com.sealmail.domain.policy.PreferredAlgorithm;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
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
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, certificateRepository, domainEventPublisher);

        EmailAddress sender = new EmailAddress("sender@example.com");
        EmailAddress recipientA = new EmailAddress("a@example.com");
        EmailAddress recipientB = new EmailAddress("b@example.com");
        byte[] payload = "hello".getBytes();
        byte[] encrypted = "encrypted".getBytes();
        when(certificateRepository.findTrustedForEncryption(recipientA))
                .thenReturn(List.of(certificate(recipientA, "cert-A", "RSA")));
        when(certificateRepository.findTrustedForEncryption(recipientB))
                .thenReturn(List.of(certificate(recipientB, "cert-B", "RSA")));
        when(smimeOperations.encryptMultiple(same(payload), any(), eq(SMIMEEncryptionSuite.STANDARD)))
                .thenReturn(encrypted);

        Message<byte[]> message = message(payload, sender, List.of(recipientA, recipientB),
                MailProcessingDecision.none().withEncryptionRequired(true),
                PreferredAlgorithm.AUTO);

        Message<byte[]> result = encryptStep.execute(message);

        assertArrayEquals(encrypted, result.getPayload());
        verify(domainEventPublisher, times(2)).publishEvent(any(MailEncrypted.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> certCaptor = ArgumentCaptor.forClass(List.class);
        verify(smimeOperations).encryptMultiple(
                same(payload),
                certCaptor.capture(),
                eq(SMIMEEncryptionSuite.STANDARD));
        assertEquals(2, certCaptor.getValue().size());
        assertTrue(certCaptor.getValue().contains("cert-A"));
        assertTrue(certCaptor.getValue().contains("cert-B"));
        verify(smimeOperations, never()).encrypt(any(), anyString());
    }

    @Test
    void mustEncryptQuarantinesWhenNoRecipientCertificateExists() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, certificateRepository, mock(DomainEventPublisher.class));

        byte[] payload = "hello".getBytes();
        Message<byte[]> message = message(payload, new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("a@example.com")),
                MailProcessingDecision.none().withMustEncrypt(true),
                PreferredAlgorithm.AUTO);

        Message<byte[]> result = encryptStep.execute(message);

        MailProcessingContext context = context(result);
        assertTrue(context.decision().requiresQuarantine());
        assertEquals("CERTIFICATE_MISSING", context.decision().quarantine().reason());
        assertEquals("DLP MUST_ENCRYPT: 未找到收件人加密证书", context.decision().quarantine().detail());
        assertEquals(MailRecordDisposition.EXCEPTION, context.recordDisposition());
        verify(smimeOperations, never()).encryptMultiple(any(), any(), any());
    }

    @Test
    void mustEncryptFailureIsRecordedAsExceptionMail() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, certificateRepository, mock(DomainEventPublisher.class));

        byte[] payload = "hello".getBytes();
        Message<byte[]> message = message(payload, new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("a@example.com")),
                MailProcessingDecision.none().withMustEncrypt(true),
                PreferredAlgorithm.AUTO);

        Message<byte[]> result = encryptStep.execute(message);

        assertTrue(context(result).decision().requiresQuarantine());
        assertEquals(MailRecordDisposition.EXCEPTION, context(result).recordDisposition());
    }

    @Test
    void mustEncryptLoadsRecipientCertificatesWhenRoutingDidNotRequireEncryption() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, certificateRepository, mock(DomainEventPublisher.class));

        EmailAddress recipient = new EmailAddress("a@example.com");
        byte[] payload = "hello".getBytes();
        byte[] encrypted = "encrypted".getBytes();
        when(certificateRepository.findTrustedForEncryption(recipient))
                .thenReturn(List.of(certificate(recipient, "cert-A", "RSA")));
        when(smimeOperations.encryptMultiple(same(payload), any(), eq(SMIMEEncryptionSuite.STANDARD)))
                .thenReturn(encrypted);

        Message<byte[]> message = message(payload, new EmailAddress("sender@example.com"),
                List.of(recipient),
                MailProcessingDecision.none().withMustEncrypt(true),
                PreferredAlgorithm.AUTO);

        Message<byte[]> result = encryptStep.execute(message);

        assertArrayEquals(encrypted, result.getPayload());
        verify(certificateRepository).findTrustedForEncryption(recipient);
        verify(smimeOperations).encryptMultiple(same(payload), any(), eq(SMIMEEncryptionSuite.STANDARD));
    }

    @Test
    void mustEncryptQuarantinesWhenAnyRecipientCertificateIsMissing() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, certificateRepository, mock(DomainEventPublisher.class));

        EmailAddress recipientA = new EmailAddress("a@example.com");
        EmailAddress recipientB = new EmailAddress("b@example.com");
        byte[] payload = "hello".getBytes();
        when(certificateRepository.findTrustedForEncryption(recipientA))
                .thenReturn(List.of(certificate(recipientA, "cert-A", "RSA")));
        when(certificateRepository.findTrustedForEncryption(recipientB))
                .thenReturn(List.of());

        Message<byte[]> message = message(payload, new EmailAddress("sender@example.com"),
                List.of(recipientA, recipientB),
                MailProcessingDecision.none().withMustEncrypt(true),
                PreferredAlgorithm.AUTO);

        Message<byte[]> result = encryptStep.execute(message);

        assertEquals("CERTIFICATE_MISSING", context(result).decision().quarantine().reason());
        assertTrue(context(result).decision().quarantine().detail().contains("b@example.com"));
        verify(smimeOperations, never()).encryptMultiple(any(), any(), any());
    }

    @Test
    void autoQuarantinesWhenRecipientsCannotShareOneEncryptionSuite() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, certificateRepository, mock(DomainEventPublisher.class));

        EmailAddress recipientA = new EmailAddress("a@example.com");
        EmailAddress recipientB = new EmailAddress("b@example.com");
        byte[] payload = "hello".getBytes();
        when(certificateRepository.findTrustedForEncryption(recipientA))
                .thenReturn(List.of(certificate(recipientA, "cert-A", "SM2")));
        when(certificateRepository.findTrustedForEncryption(recipientB))
                .thenReturn(List.of(certificate(recipientB, "cert-B", "RSA")));

        Message<byte[]> message = message(payload, new EmailAddress("sender@example.com"),
                List.of(recipientA, recipientB),
                MailProcessingDecision.none().withEncryptionRequired(true),
                PreferredAlgorithm.AUTO);

        Message<byte[]> result = encryptStep.execute(message);

        assertTrue(context(result).decision().quarantine().detail().contains("无法共享同一加密策略"));
        verify(smimeOperations, never()).encryptMultiple(any(), any(), any());
    }

    @Test
    void gmOnlyUsesGmSuiteWhenAllRecipientsHaveSm2Certificates() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, certificateRepository, mock(DomainEventPublisher.class));

        EmailAddress recipientA = new EmailAddress("a@example.com");
        EmailAddress recipientB = new EmailAddress("b@example.com");
        byte[] payload = "hello".getBytes();
        byte[] encrypted = "encrypted".getBytes();
        when(certificateRepository.findTrustedForEncryption(recipientA))
                .thenReturn(List.of(certificate(recipientA, "cert-A", "SM2")));
        when(certificateRepository.findTrustedForEncryption(recipientB))
                .thenReturn(List.of(certificate(recipientB, "cert-B", "SM2")));
        when(smimeOperations.encryptMultiple(same(payload), any(), eq(SMIMEEncryptionSuite.GM)))
                .thenReturn(encrypted);

        Message<byte[]> message = message(payload, new EmailAddress("sender@example.com"),
                List.of(recipientA, recipientB),
                MailProcessingDecision.none().withEncryptionRequired(true),
                PreferredAlgorithm.GM_ONLY);

        Message<byte[]> result = encryptStep.execute(message);

        assertArrayEquals(encrypted, result.getPayload());
        verify(smimeOperations).encryptMultiple(same(payload), any(), eq(SMIMEEncryptionSuite.GM));
    }

    @Test
    void encryptsWhenOnlyContextRequiresEncryption() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, certificateRepository, mock(DomainEventPublisher.class));

        EmailAddress recipient = new EmailAddress("a@example.com");
        byte[] payload = "hello".getBytes();
        byte[] encrypted = "encrypted".getBytes();
        when(certificateRepository.findTrustedForEncryption(recipient))
                .thenReturn(List.of(certificate(recipient, "cert-A", "RSA")));
        when(smimeOperations.encryptMultiple(same(payload), any(), eq(SMIMEEncryptionSuite.STANDARD)))
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
                .withDecision(MailProcessingDecision.none().withEncryptionRequired(true));
        Message<byte[]> message = MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();

        Message<byte[]> result = encryptStep.execute(message);

        assertArrayEquals(encrypted, result.getPayload());
        verify(smimeOperations).encryptMultiple(same(payload), any(), eq(SMIMEEncryptionSuite.STANDARD));
    }

    private static Certificate certificate(EmailAddress owner, String pemContent, String algorithm) {
        Certificate cert = Certificate.importCertificate(
                new CertificateId(UUID.randomUUID().toString()),
                owner,
                pemContent,
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

    private static Message<byte[]> message(byte[] payload,
                                           EmailAddress sender,
                                           List<EmailAddress> recipients,
                                           MailProcessingDecision decision,
                                           PreferredAlgorithm preferredAlgorithm) {
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
                .withPreferredAlgorithm(preferredAlgorithm);
        return MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }

    private static MailProcessingContext context(Message<?> message) {
        return (MailProcessingContext) message.getHeaders().get(MailProcessingHeaders.CONTEXT);
    }
}
