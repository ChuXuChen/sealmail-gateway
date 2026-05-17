package com.sealmail.infra.smtp;

import com.sealmail.domain.config.SecretReferenceResolver;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.DomainConfigRepository;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.config.properties.SmtpServerProperties;
import com.sealmail.infra.crypto.util.PemUtils;
import com.sealmail.infra.mail.pipeline.MailFlowErrorHandlingState;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import jakarta.annotation.PreDestroy;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.X509Certificate;
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
    private final SecretReferenceResolver secretReferenceResolver;
    private final MailFlowErrorHandlingState errorHandlingState;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SealMailSmtpServer(SmtpServerProperties properties,
                              @Qualifier("mailInboundChannel") MessageChannel mailInboundChannel,
                              @Qualifier("mailOutboundChannel") MessageChannel mailOutboundChannel,
                              DomainConfigRepository domainConfigRepository,
                              SecretReferenceResolver secretReferenceResolver,
                              MailFlowErrorHandlingState errorHandlingState) {
        this.mailInboundChannel = mailInboundChannel;
        this.mailOutboundChannel = mailOutboundChannel;
        this.domainConfigRepository = domainConfigRepository;
        this.secretReferenceResolver = secretReferenceResolver;
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

        if (properties.isEnableStartTls() && hasTlsMaterial(properties)) {
            configureTLS(properties);
        }

        this.smtpServer.setRequireTLS(properties.isRequireTls());
    }

    private void configureTLS(SmtpServerProperties properties) {
        try {
            if (properties.getKeystorePath() != null) {
                // Set system properties for SubEtha SMTP which uses JVM default SSL context
                System.setProperty("javax.net.ssl.keyStore", properties.getKeystorePath());
                System.setProperty("javax.net.ssl.keyStorePassword", requireSecret(
                        properties.getKeystorePasswordSecretRef(),
                        "sealmail.smtp.server.keystore-password-secret-ref"));
                System.setProperty("javax.net.ssl.keyStoreType", "PKCS12");
            } else if (properties.getCertificatePath() != null && properties.getPrivateKeyPath() != null) {
                // Create and set default SSL context for PEM certificates
                SSLContext sslContext = createSSLContext(properties);
                SSLContext.setDefault(sslContext);
            }

            this.smtpServer.setEnableTLS(true);
            log.info("STARTTLS enabled for SMTP server");
        } catch (Exception e) {
            log.warn("Failed to configure TLS for SMTP server: {} - TLS will not be available", e.getMessage());
        }
    }

    private boolean hasTlsMaterial(SmtpServerProperties properties) {
        return hasText(properties.getKeystorePath())
                || (hasText(properties.getCertificatePath()) && hasText(properties.getPrivateKeyPath()));
    }

    private SSLContext createSSLContext(SmtpServerProperties properties) throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        if (properties.getCertificatePath() != null && properties.getPrivateKeyPath() != null) {
            return createSSLContextFromPem(properties);
        } else if (properties.getKeystorePath() != null) {
            return createSSLContextFromKeystore(properties);
        } else {
            throw new IllegalArgumentException(
                    "Either keystore path or certificate/private key path must be configured for TLS");
        }
    }

    private SSLContext createSSLContextFromPem(SmtpServerProperties properties) throws Exception {
        // Read certificate
        String certPem = new String(Files.readAllBytes(Paths.get(properties.getCertificatePath())));
        X509Certificate cert = PemUtils.parseCertificate(certPem);

        // Read private key
        String keyPem = new String(Files.readAllBytes(Paths.get(properties.getPrivateKeyPath())));
        String privateKeyPassword = resolveOptionalSecret(properties.getPrivateKeyPasswordSecretRef());
        char[] keyPassword = privateKeyPassword != null
                ? privateKeyPassword.toCharArray()
                : null;
        PrivateKey privateKey = PemUtils.parsePrivateKey(keyPem, keyPassword);

        // Create in-memory keystore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("smtp", privateKey, new char[0], new java.security.cert.Certificate[]{cert});

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, new char[0]);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext;
    }

    private SSLContext createSSLContextFromKeystore(SmtpServerProperties properties) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(properties.getKeystorePath())) {
            keyStore.load(fis, requireSecret(
                    properties.getKeystorePasswordSecretRef(),
                    "sealmail.smtp.server.keystore-password-secret-ref").toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        String keystorePassword = requireSecret(
                properties.getKeystorePasswordSecretRef(),
                "sealmail.smtp.server.keystore-password-secret-ref");
        String keyPasswordSecret = resolveOptionalSecret(properties.getKeyPasswordSecretRef());
        char[] keyPassword = keyPasswordSecret != null
                ? keyPasswordSecret.toCharArray()
                : keystorePassword.toCharArray();
        kmf.init(keyStore, keyPassword);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext;
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
            this.from = new EmailAddress(from);
            log.debug("Mail from: {}", from);
        }

        @Override
        public void recipient(String recipient) {
            recipients.add(new EmailAddress(recipient));
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

    private String resolveOptionalSecret(String secretRef) {
        if (!hasText(secretRef)) {
            return null;
        }
        String secret = secretReferenceResolver.resolve(secretRef);
        return hasText(secret) ? secret : null;
    }

    private String requireSecret(String secretRef, String propertyName) {
        String secret = resolveOptionalSecret(secretRef);
        if (secret == null) {
            throw new IllegalStateException(propertyName + " must point to a resolvable secret");
        }
        return secret;
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
