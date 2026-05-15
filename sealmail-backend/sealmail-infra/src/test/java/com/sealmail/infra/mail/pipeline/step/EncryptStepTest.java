package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.KeyUsage;
import com.sealmail.domain.certificate.ValidityPeriod;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.event.MailEncrypted;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.config.properties.DlpProperties;
import com.sealmail.infra.mail.pipeline.MailRecordDisposition;
import com.sealmail.infra.mail.pipeline.PipelineResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.math.BigInteger;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        EncryptStep encryptStep = new EncryptStep(smimeOperations, certificateRepository, new DlpProperties());

        EmailAddress sender = new EmailAddress("sender@example.com");
        EmailAddress recipientA = new EmailAddress("a@example.com");
        EmailAddress recipientB = new EmailAddress("b@example.com");
        byte[] payload = "hello".getBytes();
        byte[] encrypted = "encrypted".getBytes();
        when(smimeOperations.encryptMultiple(same(payload), any())).thenReturn(encrypted);

        Map<EmailAddress, String> recipientCertificates = new LinkedHashMap<>();
        recipientCertificates.put(recipientA, "cert-A");
        recipientCertificates.put(recipientB, "cert-B");
        Map<EmailAddress, String> recipientThumbprints = new LinkedHashMap<>();
        recipientThumbprints.put(recipientA, "thumb-A");
        recipientThumbprints.put(recipientB, "thumb-B");

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
                .setHeader("recipientCertificates", recipientCertificates)
                .setHeader("recipientCertificateThumbprints", recipientThumbprints)
                .build();

        PipelineResult result = encryptStep.execute(message);

        assertTrue(result.success());
        assertArrayEquals(encrypted, result.payload());
        assertEquals(2, result.events().size());
        assertTrue(result.events().stream().allMatch(MailEncrypted.class::isInstance));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> certCaptor = ArgumentCaptor.forClass(List.class);
        verify(smimeOperations).encryptMultiple(same(payload), certCaptor.capture());
        assertEquals(2, certCaptor.getValue().size());
        assertTrue(certCaptor.getValue().contains("cert-A"));
        assertTrue(certCaptor.getValue().contains("cert-B"));
        verify(smimeOperations, never()).encrypt(any(), anyString());
    }

    @Test
    void mustEncryptQuarantinesWhenNoRecipientCertificateExists() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, certificateRepository, new DlpProperties());

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
        assertEquals(MailRecordDisposition.DLP_QUARANTINE, result.recordDisposition());
        verify(smimeOperations, never()).encryptMultiple(any(), any());
    }

    @Test
    void mustEncryptFailureCanBeRecordedAsExceptionWhenDlpQuarantineIsDisabled() {
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        DlpProperties dlpProperties = new DlpProperties();
        dlpProperties.setQuarantineEncryptionFailures(false);
        EncryptStep encryptStep = new EncryptStep(smimeOperations, certificateRepository, dlpProperties);

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
        EncryptStep encryptStep = new EncryptStep(smimeOperations, certificateRepository, new DlpProperties());

        EmailAddress recipient = new EmailAddress("a@example.com");
        byte[] payload = "hello".getBytes();
        byte[] encrypted = "encrypted".getBytes();
        when(certificateRepository.findTrustedForEncryption(recipient))
                .thenReturn(List.of(certificate(recipient, "cert-A")));
        when(smimeOperations.encryptMultiple(same(payload), any())).thenReturn(encrypted);

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
        verify(smimeOperations).encryptMultiple(same(payload), any());
    }

    private static Certificate certificate(EmailAddress owner, String pemContent) {
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
        return cert;
    }
}
