package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.CertificateSelector;
import com.sealmail.domain.certificate.KeyUsage;
import com.sealmail.domain.certificate.ValidityPeriod;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessing;
import com.sealmail.domain.mailsecurity.MailProcessingRepository;
import com.sealmail.domain.mailsecurity.MailRouter;
import com.sealmail.domain.mailsecurity.RoutingDecision;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.DomainConfigRepository;
import com.sealmail.domain.policy.PreferredAlgorithm;
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
                certificateRepository,
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
        when(mailRouter.route(any(), any(), any(), any(), any(), any()))
                .thenReturn(new RoutingDecision.OutboundEncrypt(List.of(recipient)));
        when(certificateRepository.findTrustedForSigning(sender)).thenReturn(List.of(senderCert));
        when(certificateRepository.findTrustedForEncryption(recipient)).thenReturn(List.of(recipientCert));
        when(mailProcessingRepository.save(any(MailProcessing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message<byte[]> message = MessageBuilder.withPayload("hello".getBytes())
                .setHeader("mailEnvelope", envelope)
                .build();

        Message<byte[]> routed = routingService.routeOutbound(message);

        assertEquals(Boolean.TRUE, routed.getHeaders().get("signingEnabled"));
        assertEquals(Boolean.TRUE, routed.getHeaders().get("encryptionEnabled"));
        assertEquals("127.0.0.1", routed.getHeaders().get("relayHost"));
        assertEquals(10027, routed.getHeaders().get("relayPort"));
        assertEquals("mailer@example.com", routed.getHeaders().get("relayEnvelopeFrom"));
        assertEquals(senderCert.getPemContent(), routed.getHeaders().get("senderCertificate"));
        assertNotNull(routed.getHeaders().get("senderCertificateThumbprint"));
        @SuppressWarnings("unchecked")
        var recipientCertificates = (java.util.Map<EmailAddress, String>) routed.getHeaders().get("recipientCertificates");
        assertEquals(1, recipientCertificates.size());
        assertEquals(recipientCert.getPemContent(), recipientCertificates.get(recipient));
        @SuppressWarnings("unchecked")
        var recipientThumbprints = (java.util.Map<EmailAddress, String>) routed.getHeaders().get("recipientCertificateThumbprints");
        assertEquals(recipientCert.getId().getThumbprint(), recipientThumbprints.get(recipient));
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
                certificateRepository,
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
        when(mailRouter.route(any(), any(), any(), any(), any(), any()))
                .thenReturn(new RoutingDecision.PassThrough());
        when(certificateRepository.findTrustedForSigning(sender)).thenReturn(List.of(senderCert));
        when(certificateRepository.findTrustedForEncryption(recipient)).thenReturn(List.of(recipientCert));
        when(mailProcessingRepository.save(any(MailProcessing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message<byte[]> message = MessageBuilder.withPayload("ciphertext".getBytes())
                .setHeader("mailEnvelope", envelope)
                .build();

        Message<byte[]> routed = routingService.routeInbound(message);

        assertEquals(senderCert.getPemContent(), routed.getHeaders().get("senderCertificate"));
        assertEquals(recipientCert.getPemContent(), routed.getHeaders().get("recipientCertificate"));
        assertEquals("127.0.0.1", routed.getHeaders().get("relayHost"));
        assertEquals(10026, routed.getHeaders().get("relayPort"));
        assertEquals("mailer@example.com", routed.getHeaders().get("relayEnvelopeFrom"));
        assertTrue(Boolean.TRUE.equals(routed.getHeaders().get("verificationRequired")));
        assertTrue(Boolean.TRUE.equals(routed.getHeaders().get("decryptionRequired")));
    }

    @Test
    void routeOutboundPreservesExplicitPreferredAlgorithmHeader() {
        MailRouter mailRouter = mock(MailRouter.class);
        DomainConfigRepository domainConfigRepository = mock(DomainConfigRepository.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        MailProcessingRepository mailProcessingRepository = mock(MailProcessingRepository.class);
        RoutingService routingService = new RoutingService(
                mailRouter,
                domainConfigRepository,
                certificateRepository,
                mailProcessingRepository,
                postfixProperties()
        );

        EmailAddress sender = new EmailAddress("alice@example.com");
        EmailAddress recipient = new EmailAddress("bob@example.com");
        MailEnvelope envelope = envelope(sender, recipient);
        DomainConfig config = DomainConfig.create("domain-1", "example.com", true);
        config.changePreferredAlgorithm(PreferredAlgorithm.GM_ONLY);

        when(domainConfigRepository.findByDomain("example.com")).thenReturn(Optional.of(config));
        when(mailRouter.route(any(), any(), any(), any(), any(), any()))
                .thenReturn(new RoutingDecision.OutboundEncrypt(List.of(recipient)));
        when(mailProcessingRepository.save(any(MailProcessing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message<byte[]> routed = routingService.routeOutbound(MessageBuilder.withPayload("hello".getBytes())
                .setHeader("mailEnvelope", envelope)
                .setHeader("preferredAlgorithm", PreferredAlgorithm.STANDARD_ONLY.name())
                .build());

        assertEquals(PreferredAlgorithm.STANDARD_ONLY.name(), routed.getHeaders().get("preferredAlgorithm"));
    }

    @Test
    void routeOutboundDoesNotEnableEncryptionWhenOnlySomeRecipientsHaveCertificates() {
        MailRouter mailRouter = new MailRouter(new CertificateSelector());
        DomainConfigRepository domainConfigRepository = mock(DomainConfigRepository.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        MailProcessingRepository mailProcessingRepository = mock(MailProcessingRepository.class);
        RoutingService routingService = new RoutingService(
                mailRouter,
                domainConfigRepository,
                certificateRepository,
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
                .setHeader("mailEnvelope", envelope)
                .build());

        assertEquals(null, routed.getHeaders().get("encryptionEnabled"));
        assertEquals(null, routed.getHeaders().get("recipientCertificates"));
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
                certificateRepository,
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
        when(mailRouter.route(any(), any(), any(), any(), any(), any()))
                .thenReturn(new RoutingDecision.PassThrough());
        when(mailProcessingRepository.save(any(MailProcessing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message<byte[]> routed = routingService.routeOutbound(MessageBuilder.withPayload("hello".getBytes())
                .setHeader("mailEnvelope", envelope)
                .build());

        assertEquals(Boolean.TRUE, routed.getHeaders().get("quarantineRequired"));
        assertEquals("DOMAIN_NOT_CONFIGURED", routed.getHeaders().get("quarantineReason"));
        assertEquals(MailRecordDisposition.EXCEPTION.name(), routed.getHeaders().get("mailRecordDisposition"));
        assertEquals("Sender domain is not configured and enabled as a local domain: example.com",
                routed.getHeaders().get("quarantineDetail"));
        assertEquals(null, routed.getHeaders().get("signingEnabled"));
        assertEquals(null, routed.getHeaders().get("dkimEnabled"));
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
                certificateRepository,
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
                .setHeader("mailEnvelope", envelope)
                .build());

        assertEquals(Boolean.TRUE, routed.getHeaders().get("quarantineRequired"));
        assertEquals("DOMAIN_NOT_CONFIGURED", routed.getHeaders().get("quarantineReason"));
        assertEquals(MailRecordDisposition.EXCEPTION.name(), routed.getHeaders().get("mailRecordDisposition"));
        assertEquals("No recipient domain is configured and enabled as a local domain for this mail",
                routed.getHeaders().get("quarantineDetail"));
        verify(mailRouter, never()).route(any(), any(), any(), any(), any(), any());
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
}
