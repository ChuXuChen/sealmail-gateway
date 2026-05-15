package com.sealmail.infra.mail;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.KeyUsage;
import com.sealmail.domain.certificate.ValidityPeriod;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.config.properties.PostfixProperties;
import com.sealmail.infra.config.properties.RelayProperties;
import com.sealmail.infra.mail.relay.SmtpRelayClient;
import com.sealmail.infra.mail.relay.SmtpRelayRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuarantineMailReleaseRelayImplTest {

    @Test
    void releasesOutboundMailToPostfixOutboundPort() throws Exception {
        PostfixProperties postfix = new PostfixProperties();
        postfix.setEnabled(true);
        postfix.setHost("127.0.0.1");
        postfix.setAfterFilterPort(10026);
        postfix.setOutboundPort(10027);
        postfix.setTimeout(5000);
        postfix.setEnvelopeFrom("");
        RelayProperties relay = new RelayProperties();
        relay.setUsername("");
        SmtpRelayClient smtpRelayClient = mock(SmtpRelayClient.class);
        QuarantineMailReleaseRelayImpl releaseRelay =
                new QuarantineMailReleaseRelayImpl(
                        relay,
                        postfix,
                        smtpRelayClient,
                        mock(SMIMEOperations.class),
                        mock(CertificateRepository.class));

        releaseRelay.relay(mail(MailDirection.OUTBOUND));

        ArgumentCaptor<SmtpRelayRequest> captor = ArgumentCaptor.forClass(SmtpRelayRequest.class);
        verify(smtpRelayClient).send(captor.capture());
        assertEquals(10027, captor.getValue().connection().port());
        assertEquals("sender@example.com", captor.getValue().envelopeFrom());
    }

    @Test
    void releasesInboundMailToPostfixAfterFilterPort() throws Exception {
        PostfixProperties postfix = new PostfixProperties();
        postfix.setEnabled(true);
        postfix.setHost("127.0.0.1");
        postfix.setAfterFilterPort(10026);
        postfix.setOutboundPort(10027);
        postfix.setTimeout(5000);
        postfix.setEnvelopeFrom("bounce@example.com");
        RelayProperties relay = new RelayProperties();
        SmtpRelayClient smtpRelayClient = mock(SmtpRelayClient.class);
        QuarantineMailReleaseRelayImpl releaseRelay =
                new QuarantineMailReleaseRelayImpl(
                        relay,
                        postfix,
                        smtpRelayClient,
                        mock(SMIMEOperations.class),
                        mock(CertificateRepository.class));

        releaseRelay.relay(mail(MailDirection.INBOUND));

        ArgumentCaptor<SmtpRelayRequest> captor = ArgumentCaptor.forClass(SmtpRelayRequest.class);
        verify(smtpRelayClient).send(captor.capture());
        assertEquals(10026, captor.getValue().connection().port());
        assertEquals("bounce@example.com", captor.getValue().envelopeFrom());
    }

    @Test
    void encryptsBeforeReleaseWhenRequested() throws Exception {
        PostfixProperties postfix = new PostfixProperties();
        postfix.setEnabled(true);
        postfix.setHost("127.0.0.1");
        postfix.setAfterFilterPort(10026);
        postfix.setOutboundPort(10027);
        postfix.setTimeout(5000);
        RelayProperties relay = new RelayProperties();
        SmtpRelayClient smtpRelayClient = mock(SmtpRelayClient.class);
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        EmailAddress recipient = new EmailAddress("recipient@example.com");
        when(certificateRepository.findTrustedForEncryption(recipient))
                .thenReturn(List.of(certificate(recipient, "cert-pem")));
        when(smimeOperations.encryptMultiple(any(), any())).thenReturn("encrypted".getBytes());
        QuarantineMailReleaseRelayImpl releaseRelay =
                new QuarantineMailReleaseRelayImpl(relay, postfix, smtpRelayClient, smimeOperations, certificateRepository);

        releaseRelay.relay(mail(MailDirection.OUTBOUND), true);

        ArgumentCaptor<SmtpRelayRequest> captor = ArgumentCaptor.forClass(SmtpRelayRequest.class);
        verify(smimeOperations).encryptMultiple(any(), any());
        verify(smtpRelayClient).send(captor.capture());
        assertEquals("encrypted", new String(captor.getValue().messageData()));
    }

    @Test
    void encryptedReleaseFailsWhenRecipientCertificateIsMissing() throws Exception {
        PostfixProperties postfix = new PostfixProperties();
        postfix.setEnabled(true);
        postfix.setHost("127.0.0.1");
        postfix.setAfterFilterPort(10026);
        postfix.setOutboundPort(10027);
        postfix.setTimeout(5000);
        RelayProperties relay = new RelayProperties();
        SmtpRelayClient smtpRelayClient = mock(SmtpRelayClient.class);
        SMIMEOperations smimeOperations = mock(SMIMEOperations.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        QuarantineMailReleaseRelayImpl releaseRelay =
                new QuarantineMailReleaseRelayImpl(relay, postfix, smtpRelayClient, smimeOperations, certificateRepository);

        assertThrows(QuarantineReleaseEncryptionException.class,
                () -> releaseRelay.relay(mail(MailDirection.OUTBOUND), true));

        verify(smimeOperations, never()).encryptMultiple(any(), any());
        verify(smtpRelayClient, never()).send(any());
    }

    private static QuarantinedMail mail(MailDirection direction) {
        return QuarantinedMail.create(
                "q-1",
                "msg-1",
                "subject",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                direction,
                "127.0.0.1",
                QuarantineReason.POLICY_VIOLATION,
                "detail",
                "raw".getBytes()
        );
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
