package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.KeyUsage;
import com.sealmail.domain.certificate.ValidityPeriod;
import com.sealmail.domain.certificate.spi.SMIMEEncryptionSuite;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.event.MailEncrypted;
import com.sealmail.domain.policy.PreferredAlgorithm;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.mail.pipeline.MailRecordDisposition;
import com.sealmail.infra.mail.pipeline.PipelineResult;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EncryptStepTest {

    @Test
    void encryptsAllRecipientsInOneSmimeEnvelope() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, certificateRepository);

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

        Message<byte[]> message = MessageBuilder.withPayload(payload)
                .setHeader("mailEnvelope", new MailEnvelope(
                        "msg-" + UUID.randomUUID() + "@example.com",
                        sender,
                        List.of(recipientA, recipientB),
                        "127.0.0.1",
                        "helo",
                        Instant.now(),
                        payload
                ))
                .setHeader("encryptionEnabled", true)
                .build();

        PipelineResult result = encryptStep.execute(message);

        assertTrue(result.success());
        assertArrayEquals(encrypted, result.payload());
        assertEquals(2, result.events().size());
        assertTrue(result.events().stream().allMatch(MailEncrypted.class::isInstance));

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
        EncryptStep encryptStep = new EncryptStep(smimeOperations, certificateRepository);

        byte[] payload = "hello".getBytes();
        Message<byte[]> message = MessageBuilder.withPayload(payload)
                .setHeader("mailEnvelope", new MailEnvelope(
                        "msg-" + UUID.randomUUID() + "@example.com",
                        new EmailAddress("sender@example.com"),
                        List.of(new EmailAddress("a@example.com")),
                        "127.0.0.1",
                        "helo",
                        Instant.now(),
                        payload
                ))
                .setHeader("mustEncrypt", "true")
                .build();

        PipelineResult result = encryptStep.execute(message);

        assertEquals(false, result.success());
        assertTrue(result.requiresQuarantine());
        assertEquals("CERTIFICATE_MISSING", result.quarantineReason());
        assertEquals("DLP MUST_ENCRYPT: 未找到收件人加密证书", result.quarantineDetail());
        assertEquals(MailRecordDisposition.EXCEPTION, result.recordDisposition());
        verify(smimeOperations, never()).encryptMultiple(any(), any(), any());
    }

    @Test
    void mustEncryptFailureIsRecordedAsExceptionMail() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, certificateRepository);

        byte[] payload = "hello".getBytes();
        Message<byte[]> message = MessageBuilder.withPayload(payload)
                .setHeader("mailEnvelope", new MailEnvelope(
                        "msg-" + UUID.randomUUID() + "@example.com",
                        new EmailAddress("sender@example.com"),
                        List.of(new EmailAddress("a@example.com")),
                        "127.0.0.1",
                        "helo",
                        Instant.now(),
                        payload
                ))
                .setHeader("mustEncrypt", "true")
                .build();

        PipelineResult result = encryptStep.execute(message);

        assertTrue(result.requiresQuarantine());
        assertEquals(MailRecordDisposition.EXCEPTION, result.recordDisposition());
    }

    @Test
    void mustEncryptLoadsRecipientCertificatesWhenRoutingDidNotRequireEncryption() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, certificateRepository);

        EmailAddress recipient = new EmailAddress("a@example.com");
        byte[] payload = "hello".getBytes();
        byte[] encrypted = "encrypted".getBytes();
        when(certificateRepository.findTrustedForEncryption(recipient))
                .thenReturn(List.of(certificate(recipient, "cert-A", "RSA")));
        when(smimeOperations.encryptMultiple(same(payload), any(), eq(SMIMEEncryptionSuite.STANDARD)))
                .thenReturn(encrypted);

        Message<byte[]> message = MessageBuilder.withPayload(payload)
                .setHeader("mailEnvelope", new MailEnvelope(
                        "msg-" + UUID.randomUUID() + "@example.com",
                        new EmailAddress("sender@example.com"),
                        List.of(recipient),
                        "127.0.0.1",
                        "helo",
                        Instant.now(),
                        payload
                ))
                .setHeader("mustEncrypt", "true")
                .build();

        PipelineResult result = encryptStep.execute(message);

        assertTrue(result.success());
        assertArrayEquals(encrypted, result.payload());
        verify(certificateRepository).findTrustedForEncryption(recipient);
        verify(smimeOperations).encryptMultiple(same(payload), any(), eq(SMIMEEncryptionSuite.STANDARD));
    }

    @Test
    void mustEncryptQuarantinesWhenAnyRecipientCertificateIsMissing() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, certificateRepository);

        EmailAddress recipientA = new EmailAddress("a@example.com");
        EmailAddress recipientB = new EmailAddress("b@example.com");
        byte[] payload = "hello".getBytes();
        when(certificateRepository.findTrustedForEncryption(recipientA))
                .thenReturn(List.of(certificate(recipientA, "cert-A", "RSA")));
        when(certificateRepository.findTrustedForEncryption(recipientB))
                .thenReturn(List.of());

        Message<byte[]> message = MessageBuilder.withPayload(payload)
                .setHeader("mailEnvelope", new MailEnvelope(
                        "msg-" + UUID.randomUUID() + "@example.com",
                        new EmailAddress("sender@example.com"),
                        List.of(recipientA, recipientB),
                        "127.0.0.1",
                        "helo",
                        Instant.now(),
                        payload
                ))
                .setHeader("mustEncrypt", "true")
                .build();

        PipelineResult result = encryptStep.execute(message);

        assertEquals(false, result.success());
        assertEquals("CERTIFICATE_MISSING", result.quarantineReason());
        assertTrue(result.quarantineDetail().contains("b@example.com"));
        verify(smimeOperations, never()).encryptMultiple(any(), any(), any());
    }

    @Test
    void autoQuarantinesWhenRecipientsCannotShareOneEncryptionSuite() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, certificateRepository);

        EmailAddress recipientA = new EmailAddress("a@example.com");
        EmailAddress recipientB = new EmailAddress("b@example.com");
        byte[] payload = "hello".getBytes();
        when(certificateRepository.findTrustedForEncryption(recipientA))
                .thenReturn(List.of(certificate(recipientA, "cert-A", "SM2")));
        when(certificateRepository.findTrustedForEncryption(recipientB))
                .thenReturn(List.of(certificate(recipientB, "cert-B", "RSA")));

        Message<byte[]> message = MessageBuilder.withPayload(payload)
                .setHeader("mailEnvelope", new MailEnvelope(
                        "msg-" + UUID.randomUUID() + "@example.com",
                        new EmailAddress("sender@example.com"),
                        List.of(recipientA, recipientB),
                        "127.0.0.1",
                        "helo",
                        Instant.now(),
                        payload
                ))
                .setHeader("encryptionEnabled", true)
                .build();

        PipelineResult result = encryptStep.execute(message);

        assertEquals(false, result.success());
        assertTrue(result.quarantineDetail().contains("无法共享同一加密策略"));
        verify(smimeOperations, never()).encryptMultiple(any(), any(), any());
    }

    @Test
    void gmOnlyUsesGmSuiteWhenAllRecipientsHaveSm2Certificates() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, certificateRepository);

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

        Message<byte[]> message = MessageBuilder.withPayload(payload)
                .setHeader("mailEnvelope", new MailEnvelope(
                        "msg-" + UUID.randomUUID() + "@example.com",
                        new EmailAddress("sender@example.com"),
                        List.of(recipientA, recipientB),
                        "127.0.0.1",
                        "helo",
                        Instant.now(),
                        payload
                ))
                .setHeader("encryptionEnabled", true)
                .setHeader("preferredAlgorithm", PreferredAlgorithm.GM_ONLY.name())
                .build();

        PipelineResult result = encryptStep.execute(message);

        assertTrue(result.success());
        assertArrayEquals(encrypted, result.payload());
        verify(smimeOperations).encryptMultiple(same(payload), any(), eq(SMIMEEncryptionSuite.GM));
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
}
