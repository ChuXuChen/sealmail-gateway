package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.KeyUsage;
import com.sealmail.domain.certificate.ValidityPeriod;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.CryptoProfile;
import com.sealmail.domain.mailsecurity.CryptoProfileSelector;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailProcessing;
import com.sealmail.domain.mailsecurity.MailProcessingRepository;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.mailsecurity.MailRouter;
import com.sealmail.domain.mailsecurity.RoutingDecision;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.DomainConfigRepository;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.config.properties.PostfixProperties;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.math.BigInteger;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutingServiceTest {

    @Test
    void routeOutboundEnablesSigningAndEncryptionTogether() {
        MailRouter mailRouter = mock(MailRouter.class);
        DomainConfigRepository domainConfigRepository = mock(DomainConfigRepository.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        MailProcessingRepository mailProcessingRepository = mock(MailProcessingRepository.class);
        PostfixProperties postfixProperties = postfixProperties();
        RoutingService routingService = new RoutingService(
                mailRouter,
                domainConfigRepository,
                cryptoSelectionService(certificateRepository),
                mailProcessingRepository,
                postfixProperties
        );

        EmailAddress sender = new EmailAddress("alice@example.com");
        EmailAddress recipient = new EmailAddress("bob@example.com");
        MailEnvelope envelope = envelope(sender, recipient);
        DomainConfig config = DomainConfig.create("domain-1", "example.com", true);
        config.enableSigning();
        Certificate senderCert = certificate(sender, EnumSet.of(KeyUsage.SIGNING), true, true, "RSA");
        Certificate recipientCert = certificate(recipient, EnumSet.of(KeyUsage.ENCRYPTION), true, false, "RSA");

        when(domainConfigRepository.findByDomain("example.com")).thenReturn(Optional.of(config));
        when(mailRouter.route(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new RoutingDecision.OutboundEncrypt(List.of(recipient)));
        when(certificateRepository.findTrustedForSigning(sender)).thenReturn(List.of(senderCert));
        when(certificateRepository.findTrustedForEncryption(recipient)).thenReturn(List.of(recipientCert));
        when(mailProcessingRepository.save(any(MailProcessing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message<byte[]> message = MessageBuilder.withPayload("hello".getBytes())
                .setHeader(MailProcessingHeaders.CONTEXT, MailProcessingContext.create(envelope))
                .build();

        Message<byte[]> routed = routingService.routeOutbound(message);

        MailProcessingContext context = (MailProcessingContext) routed.getHeaders().get(MailProcessingHeaders.CONTEXT);
        assertNotNull(context);
        assertEquals(envelope, context.envelope());
        assertEquals(MailDirection.OUTBOUND, context.direction());
        assertEquals(CryptoProfile.STANDARD, context.cryptoProfile());
        assertTrue(context.decision().signingRequired());
        assertTrue(context.decision().encryptionRequired());
        assertEquals(senderCert.getPemContent(), context.certificateSelection().senderCertificatePem());
        assertEquals(senderCert.getId().getThumbprint(), context.certificateSelection().senderCertificateThumbprint());
        assertEquals(recipientCert.getPemContent(), context.certificateSelection().recipientCertificates().get(recipient));
        assertEquals(recipientCert.getId().getThumbprint(),
                context.certificateSelection().recipientCertificateThumbprints().get(recipient));
        assertEquals("127.0.0.1", context.relayProfile().host());
        assertEquals(10027, context.relayProfile().port());
        assertEquals("mailer@example.com", context.relayProfile().envelopeFrom());
    }

    @Test
    void routeInboundAttachesDecryptionAndVerificationHeaders() {
        MailRouter mailRouter = mock(MailRouter.class);
        DomainConfigRepository domainConfigRepository = mock(DomainConfigRepository.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        MailProcessingRepository mailProcessingRepository = mock(MailProcessingRepository.class);
        PostfixProperties postfixProperties = postfixProperties();
        RoutingService routingService = new RoutingService(
                mailRouter,
                domainConfigRepository,
                cryptoSelectionService(certificateRepository),
                mailProcessingRepository,
                postfixProperties
        );

        EmailAddress sender = new EmailAddress("sender@example.com");
        EmailAddress recipient = new EmailAddress("local@example.com");
        MailEnvelope envelope = envelope(sender, recipient);
        Certificate senderCert = certificate(sender, EnumSet.of(KeyUsage.SIGNING), true, false, "RSA");
        Certificate recipientCert = certificate(recipient, EnumSet.of(KeyUsage.ENCRYPTION), true, true, "RSA");

        DomainConfig config = DomainConfig.create("domain-1", "example.com", true);
        when(domainConfigRepository.findByDomain("example.com")).thenReturn(Optional.of(config));
        when(mailRouter.route(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new RoutingDecision.PassThrough());
        when(certificateRepository.findTrustedForSigning(sender)).thenReturn(List.of(senderCert));
        when(certificateRepository.findTrustedForEncryption(recipient)).thenReturn(List.of(recipientCert));
        when(mailProcessingRepository.save(any(MailProcessing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message<byte[]> message = MessageBuilder.withPayload("ciphertext".getBytes())
                .setHeader(MailProcessingHeaders.CONTEXT, MailProcessingContext.create(envelope))
                .build();

        Message<byte[]> routed = routingService.routeInbound(message);

        MailProcessingContext context = (MailProcessingContext) routed.getHeaders().get(MailProcessingHeaders.CONTEXT);
        assertNotNull(context);
        assertEquals(MailDirection.INBOUND, context.direction());
        assertTrue(context.decision().verificationRequired());
        assertTrue(context.decision().decryptionRequired());
        assertEquals(senderCert.getPemContent(), context.certificateSelection().senderCertificatePem());
        assertEquals(recipientCert.getPemContent(), context.certificateSelection().recipientCertificatePem());
        assertEquals("127.0.0.1", context.relayProfile().host());
        assertEquals(10026, context.relayProfile().port());
        assertEquals("mailer@example.com", context.relayProfile().envelopeFrom());
    }

    @Test
    void routeOutboundUsesDomainPreferredAlgorithmAsCryptoProfile() {
        MailRouter mailRouter = mock(MailRouter.class);
        DomainConfigRepository domainConfigRepository = mock(DomainConfigRepository.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        MailProcessingRepository mailProcessingRepository = mock(MailProcessingRepository.class);
        RoutingService routingService = new RoutingService(
                mailRouter,
                domainConfigRepository,
                cryptoSelectionService(certificateRepository),
                mailProcessingRepository,
                postfixProperties()
        );

        EmailAddress sender = new EmailAddress("alice@example.com");
        EmailAddress recipient = new EmailAddress("bob@example.com");
        MailEnvelope envelope = envelope(sender, recipient);
        DomainConfig config = DomainConfig.create("domain-1", "example.com", true);
        config.changePreferredAlgorithm(com.sealmail.domain.policy.PreferredAlgorithm.GM_ONLY);

        when(domainConfigRepository.findByDomain("example.com")).thenReturn(Optional.of(config));
        when(mailRouter.route(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new RoutingDecision.OutboundEncrypt(List.of(recipient)));
        when(mailProcessingRepository.save(any(MailProcessing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message<byte[]> routed = routingService.routeOutbound(MessageBuilder.withPayload("hello".getBytes())
                .setHeader(MailProcessingHeaders.CONTEXT, MailProcessingContext.create(envelope))
                .build());

        MailProcessingContext context = (MailProcessingContext) routed.getHeaders().get(MailProcessingHeaders.CONTEXT);
        assertEquals(CryptoProfile.GM, context.cryptoProfile());
    }

    @Test
    void routeOutboundDoesNotEnableEncryptionWhenOnlySomeRecipientsHaveCertificates() {
        MailRouter mailRouter = new MailRouter();
        DomainConfigRepository domainConfigRepository = mock(DomainConfigRepository.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        MailProcessingRepository mailProcessingRepository = mock(MailProcessingRepository.class);
        RoutingService routingService = new RoutingService(
                mailRouter,
                domainConfigRepository,
                cryptoSelectionService(certificateRepository),
                mailProcessingRepository,
                postfixProperties()
        );

        EmailAddress sender = new EmailAddress("alice@example.com");
        EmailAddress certified = new EmailAddress("2416507029@qq.com");
        EmailAddress missing = new EmailAddress("1261017453@qq.com");
        MailEnvelope envelope = new MailEnvelope(
                "msg-" + UUID.randomUUID() + "@example.com",
                sender,
                List.of(certified, missing),
                "127.0.0.1",
                "helo",
                Instant.now(),
                "body".getBytes()
        );
        DomainConfig config = DomainConfig.create("domain-1", "example.com", true);
        Certificate recipientCert = certificate(certified, EnumSet.of(KeyUsage.ENCRYPTION), true, false, "RSA");

        when(domainConfigRepository.findByDomain("example.com")).thenReturn(Optional.of(config));
        when(certificateRepository.findTrustedForEncryption(certified)).thenReturn(List.of(recipientCert));
        when(certificateRepository.findTrustedForEncryption(missing)).thenReturn(List.of());
        when(mailProcessingRepository.save(any(MailProcessing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message<byte[]> routed = routingService.routeOutbound(MessageBuilder.withPayload("hello".getBytes())
                .setHeader(MailProcessingHeaders.CONTEXT, MailProcessingContext.create(envelope))
                .build());

        MailProcessingContext context = (MailProcessingContext) routed.getHeaders().get(MailProcessingHeaders.CONTEXT);
        assertEquals(false, context.decision().encryptionRequired());
        assertEquals(true, context.certificateSelection().recipientCertificates().isEmpty());
    }

    @Test
    void routeOutboundSendsCryptoProfileSelectionFailureToErrorFlow() {
        MailRouter mailRouter = mock(MailRouter.class);
        DomainConfigRepository domainConfigRepository = mock(DomainConfigRepository.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        MailProcessingRepository mailProcessingRepository = mock(MailProcessingRepository.class);
        RoutingService routingService = new RoutingService(
                mailRouter,
                domainConfigRepository,
                cryptoSelectionService(certificateRepository),
                mailProcessingRepository,
                postfixProperties()
        );

        EmailAddress sender = new EmailAddress("alice@example.com");
        EmailAddress recipient = new EmailAddress("bob@example.com");
        MailEnvelope envelope = envelope(sender, recipient);
        DomainConfig config = DomainConfig.create("domain-1", "example.com", true);

        when(domainConfigRepository.findByDomain("example.com")).thenReturn(Optional.of(config));
        when(mailRouter.route(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new RoutingDecision.Quarantine(
                        QuarantineReason.CERTIFICATE_MISSING,
                        "多收件人无法共享同一加密Profile"));
        when(mailProcessingRepository.save(any(MailProcessing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MailProcessingException error = assertThrows(MailProcessingException.class, () ->
                routingService.routeOutbound(MessageBuilder.withPayload("hello".getBytes())
                        .setHeader(MailProcessingHeaders.CONTEXT, MailProcessingContext.create(envelope))
                        .build()));

        assertEquals(MailProcessingErrorType.ENCRYPTION, error.errorType());
        assertTrue(error.getMessage().contains("无法共享同一加密Profile"));
    }

    @Test
    void routeOutboundQuarantinesWhenSenderDomainIsNotActiveLocalDomain() {
        MailRouter mailRouter = mock(MailRouter.class);
        DomainConfigRepository domainConfigRepository = mock(DomainConfigRepository.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        MailProcessingRepository mailProcessingRepository = mock(MailProcessingRepository.class);
        RoutingService routingService = new RoutingService(
                mailRouter,
                domainConfigRepository,
                cryptoSelectionService(certificateRepository),
                mailProcessingRepository,
                postfixProperties()
        );

        EmailAddress sender = new EmailAddress("alice@example.com");
        EmailAddress recipient = new EmailAddress("bob@example.com");
        MailEnvelope envelope = envelope(sender, recipient);
        DomainConfig inactive = DomainConfig.create("domain-1", "example.com", true);
        inactive.enableSigning();
        inactive.setDkimEnabled(true);
        inactive.deactivate();

        when(domainConfigRepository.findByDomain("example.com")).thenReturn(Optional.of(inactive));
        when(mailRouter.route(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new RoutingDecision.PassThrough());
        when(mailProcessingRepository.save(any(MailProcessing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message<byte[]> routed = routingService.routeOutbound(MessageBuilder.withPayload("hello".getBytes())
                .setHeader(MailProcessingHeaders.CONTEXT, MailProcessingContext.create(envelope))
                .build());

        MailProcessingContext context = (MailProcessingContext) routed.getHeaders().get(MailProcessingHeaders.CONTEXT);
        assertNotNull(context);
        assertTrue(context.decision().requiresQuarantine());
        assertEquals("DOMAIN_NOT_CONFIGURED", context.decision().quarantine().reason());
        assertEquals(MailRecordDisposition.EXCEPTION, context.recordDisposition());
        assertEquals("Sender domain is not configured and enabled as a local domain: example.com",
                context.decision().quarantine().detail());
        assertEquals(false, context.decision().signingRequired());
        assertEquals(false, context.decision().dkimSigningRequired());
        verify(certificateRepository, never()).findTrustedForSigning(sender);
    }

    @Test
    void routeInboundQuarantinesWhenNoRecipientDomainIsActiveLocal() {
        MailRouter mailRouter = mock(MailRouter.class);
        DomainConfigRepository domainConfigRepository = mock(DomainConfigRepository.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        MailProcessingRepository mailProcessingRepository = mock(MailProcessingRepository.class);
        RoutingService routingService = new RoutingService(
                mailRouter,
                domainConfigRepository,
                cryptoSelectionService(certificateRepository),
                mailProcessingRepository,
                postfixProperties()
        );

        EmailAddress sender = new EmailAddress("sender@remote.test");
        EmailAddress recipient = new EmailAddress("local@example.com");
        MailEnvelope envelope = envelope(sender, recipient);
        DomainConfig inactive = DomainConfig.create("domain-1", "example.com", true);
        inactive.deactivate();

        when(domainConfigRepository.findByDomain("example.com")).thenReturn(Optional.of(inactive));
        when(mailProcessingRepository.save(any(MailProcessing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message<byte[]> routed = routingService.routeInbound(MessageBuilder.withPayload("hello".getBytes())
                .setHeader(MailProcessingHeaders.CONTEXT, MailProcessingContext.create(envelope))
                .build());

        MailProcessingContext context = (MailProcessingContext) routed.getHeaders().get(MailProcessingHeaders.CONTEXT);
        assertTrue(context.decision().requiresQuarantine());
        assertEquals("DOMAIN_NOT_CONFIGURED", context.decision().quarantine().reason());
        assertEquals(MailRecordDisposition.EXCEPTION, context.recordDisposition());
        assertEquals("No recipient domain is configured and enabled as a local domain for this mail",
                context.decision().quarantine().detail());
        verify(mailRouter, never()).route(any(), any(), any(), any(), any(), any(), any());
    }

    private static MailEnvelope envelope(EmailAddress sender, EmailAddress recipient) {
        return new MailEnvelope(
                "msg-" + UUID.randomUUID() + "@example.com",
                sender,
                List.of(recipient),
                "127.0.0.1",
                "helo",
                Instant.now(),
                "body".getBytes()
        );
    }

    private static Certificate certificate(EmailAddress owner,
                                           Set<KeyUsage> usages,
                                           boolean trusted,
                                           boolean withPrivateKey,
                                           String algorithm) {
        Certificate cert = Certificate.importCertificate(
                new CertificateId(UUID.randomUUID().toString()),
                owner,
                "pem-" + UUID.randomUUID(),
                new ValidityPeriod(Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600)),
                usages,
                "CN=issuer",
                "CN=subject",
                BigInteger.ONE,
                "ski-" + UUID.randomUUID()
        );
        cert.setAlgorithm(algorithm);
        if (trusted) {
            cert.trust();
        }
        if (withPrivateKey) {
            cert.setPrivateKeyData("private-key-" + UUID.randomUUID());
        }
        return cert;
    }

    private static PostfixProperties postfixProperties() {
        PostfixProperties properties = new PostfixProperties();
        properties.setEnabled(true);
        properties.setHost("127.0.0.1");
        properties.setAfterFilterPort(10026);
        properties.setOutboundPort(10027);
        properties.setEnvelopeFrom("mailer@example.com");
        properties.setUseTls(false);
        properties.setTimeout(10000);
        return properties;
    }

    private static MailCryptoSelectionService cryptoSelectionService(CertificateRepository certificateRepository) {
        return new MailCryptoSelectionService(certificateRepository, new CryptoProfileSelector());
    }
}
