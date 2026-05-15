package com.sealmail.infra.smtp;

import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.DomainConfigRepository;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.config.properties.SmtpServerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageChannel;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SealMailSmtpServerTest {

    @Test
    void routesConfiguredLocalSenderDomainAsOutbound() {
        DomainConfigRepository domainConfigRepository = mock(DomainConfigRepository.class);
        DomainConfig localDomain = DomainConfig.create("domain-1", "example.com", true);
        when(domainConfigRepository.findByDomain("example.com")).thenReturn(Optional.of(localDomain));

        SealMailSmtpServer server = server(domainConfigRepository);

        assertEquals(MailDirection.OUTBOUND, server.resolveDirection(envelope("alice@example.com", "bob@remote.test")));
    }

    @Test
    void routesRemoteSenderDomainAsInbound() {
        DomainConfigRepository domainConfigRepository = mock(DomainConfigRepository.class);
        when(domainConfigRepository.findByDomain("remote.test")).thenReturn(Optional.empty());
        DomainConfig localDomain = DomainConfig.create("domain-1", "example.com", true);
        when(domainConfigRepository.findByDomain("example.com")).thenReturn(Optional.of(localDomain));

        SealMailSmtpServer server = server(domainConfigRepository);

        assertEquals(MailDirection.INBOUND, server.resolveDirection(envelope("alice@remote.test", "bob@example.com")));
    }

    @Test
    void routesConfiguredInactiveLocalSenderDomainAsOutboundForExceptionRecording() {
        DomainConfigRepository domainConfigRepository = mock(DomainConfigRepository.class);
        DomainConfig inactiveLocalDomain = DomainConfig.create("domain-1", "example.com", true);
        inactiveLocalDomain.deactivate();
        when(domainConfigRepository.findByDomain("example.com")).thenReturn(Optional.of(inactiveLocalDomain));

        SealMailSmtpServer server = server(domainConfigRepository);

        assertEquals(MailDirection.OUTBOUND, server.resolveDirection(envelope("alice@example.com", "bob@remote.test")));
    }

    @Test
    void defaultsToInboundWhenNeitherSenderNorRecipientBelongsToConfiguredLocalDomain() {
        DomainConfigRepository domainConfigRepository = mock(DomainConfigRepository.class);
        when(domainConfigRepository.findByDomain("remote.test")).thenReturn(Optional.empty());
        when(domainConfigRepository.findByDomain("other.test")).thenReturn(Optional.empty());

        SealMailSmtpServer server = server(domainConfigRepository);

        assertEquals(MailDirection.INBOUND, server.resolveDirection(envelope("alice@remote.test", "bob@other.test")));
    }

    private static SealMailSmtpServer server(DomainConfigRepository domainConfigRepository) {
        SmtpServerProperties properties = new SmtpServerProperties();
        properties.setPort(0);
        return new SealMailSmtpServer(
                properties,
                mock(MessageChannel.class),
                mock(MessageChannel.class),
                domainConfigRepository
        );
    }

    private static MailEnvelope envelope(String sender, String recipient) {
        return new MailEnvelope(
                "msg-" + java.util.UUID.randomUUID() + "@example.com",
                new EmailAddress(sender),
                List.of(new EmailAddress(recipient)),
                "127.0.0.1",
                "helo",
                Instant.now(),
                "Subject: Test\r\n\r\nBody".getBytes()
        );
    }
}
