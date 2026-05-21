package com.sealmail.infra.smtp;

import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.DomainConfigRepository;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.config.properties.SmtpServerProperties;
import com.sealmail.infra.mail.pipeline.MailFlowErrorHandlingState;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.subethamail.smtp.util.EmailUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    @Test
    void contentFilterMailAcknowledgesNonRetryableFailureAfterErrorFlowHandledIt() {
        DomainConfigRepository domainConfigRepository = mock(DomainConfigRepository.class);
        DomainConfig localDomain = DomainConfig.create("domain-1", "example.com", true);
        when(domainConfigRepository.findByDomain("example.com")).thenReturn(Optional.of(localDomain));
        MessageChannel outboundChannel = mock(MessageChannel.class);
        MailFlowErrorHandlingState errorHandlingState = new MailFlowErrorHandlingState();
        SealMailSmtpServer server = server(domainConfigRepository, mock(MessageChannel.class),
                outboundChannel, errorHandlingState);
        MailEnvelope envelope = envelope("alice@example.com", "bob@remote.test");
        MailProcessingException missingCertificate = new MailProcessingException(
                MailProcessingErrorType.ENCRYPTION,
                "以下收件人没有加密证书: bob@remote.test",
                null);
        when(outboundChannel.send(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    errorHandlingState.markHandled();
                    throw new MessageDeliveryException(invocation.getArgument(0), "pipeline failed", missingCertificate);
                });

        assertDoesNotThrow(() -> server.dispatchContentFilterMail("Subject: T\r\n\r\nBody".getBytes(), envelope));

        verify(outboundChannel).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void contentFilterMailKeepsTemporaryFailureWhenErrorFlowCouldNotPersist() {
        DomainConfigRepository domainConfigRepository = mock(DomainConfigRepository.class);
        DomainConfig localDomain = DomainConfig.create("domain-1", "example.com", true);
        when(domainConfigRepository.findByDomain("example.com")).thenReturn(Optional.of(localDomain));
        MessageChannel outboundChannel = mock(MessageChannel.class);
        MailFlowErrorHandlingState errorHandlingState = new MailFlowErrorHandlingState();
        SealMailSmtpServer server = server(domainConfigRepository, mock(MessageChannel.class),
                outboundChannel, errorHandlingState);
        MailEnvelope envelope = envelope("alice@example.com", "bob@remote.test");
        MailProcessingException missingCertificate = new MailProcessingException(
                MailProcessingErrorType.ENCRYPTION,
                "以下收件人没有加密证书: bob@remote.test",
                null);
        when(outboundChannel.send(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    errorHandlingState.markFailed(new IllegalStateException("exception mail save failed"));
                    throw new MessageDeliveryException(invocation.getArgument(0), "pipeline failed", missingCertificate);
                });

        assertThrows(MessageDeliveryException.class,
                () -> server.dispatchContentFilterMail("Subject: T\r\n\r\nBody".getBytes(), envelope));
    }

    @Test
    void contentFilterMailKeepsTemporaryFailureForRetryableError() {
        DomainConfigRepository domainConfigRepository = mock(DomainConfigRepository.class);
        DomainConfig localDomain = DomainConfig.create("domain-1", "example.com", true);
        when(domainConfigRepository.findByDomain("example.com")).thenReturn(Optional.of(localDomain));
        MessageChannel outboundChannel = mock(MessageChannel.class);
        MailFlowErrorHandlingState errorHandlingState = new MailFlowErrorHandlingState();
        SealMailSmtpServer server = server(domainConfigRepository, mock(MessageChannel.class),
                outboundChannel, errorHandlingState);
        MailEnvelope envelope = envelope("alice@example.com", "bob@remote.test");
        MailProcessingException relayDown = new MailProcessingException(
                MailProcessingErrorType.RELAY,
                "relay down",
                null,
                com.sealmail.domain.mailsecurity.MailRecordDisposition.EXCEPTION,
                true,
                null);
        when(outboundChannel.send(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    errorHandlingState.markHandled();
                    throw new MessageDeliveryException(invocation.getArgument(0), "pipeline failed", relayDown);
                });

        assertThrows(MessageDeliveryException.class,
                () -> server.dispatchContentFilterMail("Subject: T\r\n\r\nBody".getBytes(), envelope));
    }

    @Test
    void normalizesSmtpPathAndParametersBeforeEmailValidation() {
        assertEquals("alice@example.com", SealMailSmtpServer.normalizeSmtpMailbox("<alice@example.com>"));
        assertEquals("alice@example.com", SealMailSmtpServer.normalizeSmtpMailbox("<alice@example.com> SIZE=123"));
        assertEquals("alice@example.com", SealMailSmtpServer.normalizeSmtpMailbox("MAIL FROM:<alice@example.com> SIZE=123"));
        assertEquals("bob@example.net", SealMailSmtpServer.normalizeSmtpMailbox("RCPT TO:<bob@example.net>"));
        assertEquals("carol@example.org", SealMailSmtpServer.normalizeSmtpMailbox("carol@example.org BODY=8BITMIME"));
    }

    @Test
    void subethaAddressValidationWorksWithJavaMailApiOnlyClasspath() {
        assertTrue(EmailUtils.isValidEmailAddress("alice@example.com"));
    }

    private static SealMailSmtpServer server(DomainConfigRepository domainConfigRepository) {
        return server(domainConfigRepository, mock(MessageChannel.class), mock(MessageChannel.class),
                new MailFlowErrorHandlingState());
    }

    private static SealMailSmtpServer server(DomainConfigRepository domainConfigRepository,
                                             MessageChannel inboundChannel,
                                             MessageChannel outboundChannel,
                                             MailFlowErrorHandlingState errorHandlingState) {
        SmtpServerProperties properties = new SmtpServerProperties();
        properties.setPort(0);
        return new SealMailSmtpServer(
                properties,
                inboundChannel,
                outboundChannel,
                domainConfigRepository,
                errorHandlingState
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
