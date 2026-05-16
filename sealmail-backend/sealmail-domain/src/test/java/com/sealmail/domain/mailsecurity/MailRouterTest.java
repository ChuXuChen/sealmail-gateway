package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.KeyUsage;
import com.sealmail.domain.certificate.ValidityPeriod;
import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.EncryptionPolicy;
import com.sealmail.domain.policy.PolicyPattern;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MailRouterTest {

    private final MailRouter router = new MailRouter();

    @Test
    void optionalEncryptionDoesNotEncryptWhenOnlySomeRecipientsHaveCertificates() {
        EmailAddress sender = new EmailAddress("sender@example.com");
        EmailAddress certified = new EmailAddress("2416507029@qq.com");
        EmailAddress missing = new EmailAddress("1261017453@qq.com");
        MailEnvelope envelope = envelope(sender, List.of(certified, missing));
        DomainConfig config = DomainConfig.create("domain-1", "example.com", true);
        config.changePolicy(EncryptionPolicy.ALLOW);

        RoutingDecision decision = router.route(
                envelope,
                MailDirection.OUTBOUND,
                config,
                List.of(certificate(certified, "RSA")),
                List.of(),
                "hello",
                CryptoProfile.AUTO);

        assertInstanceOf(RoutingDecision.PassThrough.class, decision);
    }

    @Test
    void mandatoryEncryptionQuarantinesWhenAnyRecipientCertificateIsMissing() {
        EmailAddress sender = new EmailAddress("sender@example.com");
        EmailAddress certified = new EmailAddress("2416507029@qq.com");
        EmailAddress missing = new EmailAddress("1261017453@qq.com");
        MailEnvelope envelope = envelope(sender, List.of(certified, missing));
        DomainConfig config = DomainConfig.create("domain-1", "example.com", true);
        config.changePolicy(EncryptionPolicy.MANDATORY);

        RoutingDecision decision = router.route(
                envelope,
                MailDirection.OUTBOUND,
                config,
                List.of(certificate(certified, "RSA")),
                List.of(),
                "hello",
                CryptoProfile.AUTO);

        RoutingDecision.Quarantine quarantine = assertInstanceOf(RoutingDecision.Quarantine.class, decision);
        assertEquals(QuarantineReason.CERTIFICATE_MISSING, quarantine.getReason());
        assertEquals("以下收件人没有加密证书: 1261017453@qq.com", quarantine.getDetail());
    }

    @Test
    void dlpMustEncryptQuarantinesWhenAnyRecipientCertificateIsMissing() {
        EmailAddress sender = new EmailAddress("sender@example.com");
        EmailAddress certified = new EmailAddress("2416507029@qq.com");
        EmailAddress missing = new EmailAddress("1261017453@qq.com");
        MailEnvelope envelope = envelope(sender, List.of(certified, missing));
        DomainConfig config = DomainConfig.create("domain-1", "example.com", true);

        RoutingDecision decision = router.route(
                envelope,
                MailDirection.OUTBOUND,
                config,
                List.of(certificate(certified, "RSA")),
                List.of(new PolicyPattern("encrypt", "secret", 1, DispositionAction.MUST_ENCRYPT)),
                "secret",
                CryptoProfile.AUTO);

        RoutingDecision.Quarantine quarantine = assertInstanceOf(RoutingDecision.Quarantine.class, decision);
        assertEquals(QuarantineReason.CERTIFICATE_MISSING, quarantine.getReason());
        assertEquals("以下收件人没有加密证书: 1261017453@qq.com", quarantine.getDetail());
    }

    @Test
    void encryptsOnlyWhenAllRecipientsShareOneSuite() {
        EmailAddress sender = new EmailAddress("sender@example.com");
        EmailAddress recipientA = new EmailAddress("a@example.com");
        EmailAddress recipientB = new EmailAddress("b@example.com");
        MailEnvelope envelope = envelope(sender, List.of(recipientA, recipientB));
        DomainConfig config = DomainConfig.create("domain-1", "example.com", true);
        config.changePolicy(EncryptionPolicy.ALLOW);

        RoutingDecision decision = router.route(
                envelope,
                MailDirection.OUTBOUND,
                config,
                List.of(certificate(recipientA, "RSA"), certificate(recipientB, "RSA")),
                List.of(),
                "hello",
                CryptoProfile.AUTO);

        RoutingDecision.OutboundEncrypt encrypt = assertInstanceOf(RoutingDecision.OutboundEncrypt.class, decision);
        assertEquals(envelope.getRecipients(), encrypt.getRecipients());
    }

    private static MailEnvelope envelope(EmailAddress sender, List<EmailAddress> recipients) {
        return new MailEnvelope(
                "msg-" + UUID.randomUUID() + "@example.com",
                sender,
                recipients,
                "127.0.0.1",
                "helo",
                Instant.now(),
                "hello".getBytes()
        );
    }

    private static Certificate certificate(EmailAddress owner, String algorithm) {
        Certificate cert = Certificate.importCertificate(
                new CertificateId(UUID.randomUUID().toString()),
                owner,
                "pem-" + UUID.randomUUID(),
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
