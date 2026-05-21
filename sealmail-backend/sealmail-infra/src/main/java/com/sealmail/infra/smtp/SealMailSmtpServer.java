package com.sealmail.infra.smtp;

import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.DomainConfigRepository;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.config.properties.SmtpServerProperties;
import com.sealmail.infra.mail.pipeline.MailFlowErrorHandlingState;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.server.SMTPServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SealMailSmtpServer {

    private static final Logger log = LoggerFactory.getLogger(SealMailSmtpServer.class);

    private final SMTPServer smtpServer;
    private final MessageChannel mailInboundChannel;
    private final MessageChannel mailOutboundChannel;
    private final DomainConfigRepository domainConfigRepository;
    private final MailFlowErrorHandlingState errorHandlingState;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SealMailSmtpServer(SmtpServerProperties properties,
                              @Qualifier("mailInboundChannel") MessageChannel mailInboundChannel,
                              @Qualifier("mailOutboundChannel") MessageChannel mailOutboundChannel,
                              DomainConfigRepository domainConfigRepository,
                              MailFlowErrorHandlingState errorHandlingState) {
        this.mailInboundChannel = mailInboundChannel;
        this.mailOutboundChannel = mailOutboundChannel;
        this.domainConfigRepository = domainConfigRepository;
        this.errorHandlingState = errorHandlingState;
        this.smtpServer = new SMTPServer(new SealMailMessageHandlerFactory());
        this.smtpServer.setPort(properties.getPort());
        try {
            this.smtpServer.setBindAddress(java.net.InetAddress.getByName(properties.getBindAddress()));
        } catch (Exception e) {
            throw new RuntimeException("Invalid bind address: " + properties.getBindAddress(), e);
        }
        this.smtpServer.setMaxConnections(properties.getMaxConnections());
        this.smtpServer.setMaxMessageSize(properties.getMaxMessageSize());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        smtpServer.start();
        log.info("SMTP Server started on {}:{}", smtpServer.getBindAddress(), smtpServer.getPort());
    }

    @PreDestroy
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        smtpServer.stop();
        log.info("SMTP Server stopped");
    }

    private class SealMailMessageHandlerFactory implements MessageHandlerFactory {
        @Override
        public MessageHandler create(MessageContext ctx) {
            return new SealMailMessageHandler(ctx);
        }
    }

    private class SealMailMessageHandler implements MessageHandler {

        private final MessageContext ctx;
        private EmailAddress from;
        private List<EmailAddress> recipients = new ArrayList<>();

        public SealMailMessageHandler(MessageContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void from(String from) {
            this.from = new EmailAddress(normalizeSmtpMailbox(from));
            log.debug("Mail from: {}", from);
        }

        @Override
        public void recipient(String recipient) {
            recipients.add(new EmailAddress(normalizeSmtpMailbox(recipient)));
            log.debug("Mail recipient: {}", recipient);
        }

        @Override
        public void data(InputStream data) throws IOException {
            byte[] content = readAllBytes(data);
            log.debug("Received mail: {} bytes from {} to {} recipients",
                    content.length, from, recipients.size());

            InetSocketAddress remoteAddr = (InetSocketAddress) ctx.getRemoteAddress();

            MailEnvelope envelope = new MailEnvelope(
                    "<" + java.util.UUID.randomUUID() + "@sealmail.local>",
                    from,
                    recipients,
                    remoteAddr.getAddress().getHostAddress(),
                    null,
                    java.time.Instant.now(),
                    content
            );

            dispatchContentFilterMail(content, envelope);
        }

        @Override
        public void done() {
        }
    }

    void dispatchContentFilterMail(byte[] content, MailEnvelope envelope) throws IOException {
        MailDirection direction = resolveDirection(envelope);
        MessageChannel targetChannel = direction == MailDirection.OUTBOUND
                ? mailOutboundChannel
                : mailInboundChannel;

        log.debug("Dispatching content-filter mail as {} from {} to {} recipients",
                direction, envelope.getSender(), envelope.getRecipients().size());

        var context = MailProcessingContext.initial(
                envelope,
                direction,
                "content_filter",
                content,
                null,
                envelope.getRemoteHost());
        var builder = MessageBuilder
                .withPayload(content)
                .setHeader(MailProcessingHeaders.CONTEXT, context);
        try {
            errorHandlingState.clear();
            boolean accepted = targetChannel.send(builder.build());
            if (!accepted) {
                throw new IOException("Mail processing channel rejected message");
            }
        } catch (RuntimeException e) {
            MailProcessingException processingException = findMailProcessingException(e);
            MailFlowErrorHandlingState.Result errorHandlingResult = errorHandlingState.take();
            if (processingException != null
                    && !processingException.retryable()
                    && errorHandlingResult != null
                    && errorHandlingResult.handled()) {
                log.warn("Accepted content-filter mail {} after non-retryable {} failure: {}",
                        envelope.getMessageId(),
                        processingException.errorType(),
                        processingException.getMessage());
                return;
            }
            if (errorHandlingResult != null && errorHandlingResult.failure() != null) {
                log.warn("Content-filter mail {} remains retryable because error handling failed: {}",
                        envelope.getMessageId(), errorHandlingResult.failure().getMessage());
            }
            throw e;
        }
    }

    private byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    static String normalizeSmtpMailbox(String value) {
        if (value == null) {
            return null;
        }
        String mailbox = value.trim();
        if (mailbox.regionMatches(true, 0, "MAIL FROM:", 0, "MAIL FROM:".length())) {
            mailbox = mailbox.substring("MAIL FROM:".length()).trim();
        } else if (mailbox.regionMatches(true, 0, "RCPT TO:", 0, "RCPT TO:".length())) {
            mailbox = mailbox.substring("RCPT TO:".length()).trim();
        }
        if (mailbox.startsWith("<")) {
            int end = mailbox.indexOf('>');
            if (end > 0) {
                mailbox = mailbox.substring(1, end).trim();
            }
        } else {
            int parameterStart = mailbox.indexOf(' ');
            if (parameterStart > 0) {
                mailbox = mailbox.substring(0, parameterStart).trim();
            }
        }
        return mailbox;
    }

    private MailProcessingException findMailProcessingException(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof MailProcessingException mailProcessingException) {
                return mailProcessingException;
            }
            current = current.getCause();
        }
        return null;
    }

    MailDirection resolveDirection(MailEnvelope envelope) {
        if (isConfiguredLocalDomain(envelope.getSender().getDomain())) {
            return MailDirection.OUTBOUND;
        }
        if (hasConfiguredLocalRecipientDomain(envelope)) {
            return MailDirection.INBOUND;
        }
        return MailDirection.INBOUND;
    }

    private boolean isConfiguredLocalDomain(String domain) {
        return domainConfigRepository.findByDomain(domain)
                .map(DomainConfig::isLocalDomain)
                .orElse(false);
    }

    private boolean hasConfiguredLocalRecipientDomain(MailEnvelope envelope) {
        return envelope.getRecipients().stream()
                .map(EmailAddress::getDomain)
                .anyMatch(this::isConfiguredLocalDomain);
    }
}
