package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.DeliveryRoute;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.RelayProfile;
import com.sealmail.domain.policy.DeliveryTransportProfile;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.config.properties.PostfixProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RelayProfileResolverTest {

    @Test
    void resolvesInboundToPostfixAfterFilterPort() {
        RelayProfileResolver resolver = new RelayProfileResolver(
                postfixProperties(true),
                recipients -> Optional.empty(),
                "127.0.0.1",
                2526);

        RelayProfile profile = resolver.resolve(MailDirection.INBOUND, envelope());

        assertEquals("127.0.0.1", profile.host());
        assertEquals(10026, profile.port());
        assertEquals("mailer@example.com", profile.envelopeFrom());
    }

    @Test
    void resolvesOutboundToDomainDeliveryRouteWhenConfigured() {
        RelayProfileResolver resolver = new RelayProfileResolver(
                postfixProperties(true),
                recipients -> Optional.of(new DeliveryRoute(
                        "partner.test",
                        "smtp.partner.test",
                        DeliveryTransportProfile.SMTP_STARTTLS_STANDARD,
                        587)),
                "127.0.0.1",
                2526);

        RelayProfile profile = resolver.resolve(MailDirection.OUTBOUND, envelope());

        assertEquals("smtp.partner.test", profile.host());
        assertEquals(587, profile.port());
        assertEquals(DeliveryTransportProfile.SMTP_STARTTLS_STANDARD, profile.transportProfile());
        assertEquals("mailer@example.com", profile.envelopeFrom());
    }

    @Test
    void resolvesGmDeliveryRouteToEdgeSmartHost() {
        RelayProfileResolver resolver = new RelayProfileResolver(
                postfixProperties(true),
                recipients -> Optional.of(new DeliveryRoute(
                        "partner.test",
                        "gm.partner.test",
                        DeliveryTransportProfile.SMTP_IMPLICIT_TLS_GM,
                        2465)),
                "gm-edge.local",
                2527);

        RelayProfile profile = resolver.resolve(MailDirection.OUTBOUND, envelope());

        assertEquals("gm-edge.local", profile.host());
        assertEquals(2527, profile.port());
        assertEquals(DeliveryTransportProfile.SMTP_CLEAR, profile.transportProfile());
    }

    @Test
    void returnsNoProfileWhenPostfixIsDisabledAndNoDomainRouteExists() {
        RelayProfileResolver resolver = new RelayProfileResolver(
                postfixProperties(false),
                recipients -> Optional.empty(),
                "127.0.0.1",
                2526);

        assertNull(resolver.resolve(MailDirection.OUTBOUND, envelope()));
    }

    private static PostfixProperties postfixProperties(boolean enabled) {
        PostfixProperties properties = new PostfixProperties();
        properties.setEnabled(enabled);
        properties.setHost("127.0.0.1");
        properties.setAfterFilterPort(10026);
        properties.setOutboundPort(10027);
        properties.setEnvelopeFrom("mailer@example.com");
        properties.setTimeout(10000);
        return properties;
    }

    private static MailEnvelope envelope() {
        return new MailEnvelope(
                "msg-" + UUID.randomUUID() + "@example.com",
                new EmailAddress("alice@example.com"),
                List.of(new EmailAddress("bob@partner.test")),
                "127.0.0.1",
                "helo",
                Instant.now(),
                "body".getBytes()
        );
    }
}
