package com.sealmail.infra.mail;

import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mail.spi.OutboundMailSubmitter;
import com.sealmail.domain.shared.model.EmailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboundMailGateway implements OutboundMailSubmitter {

    private static final Logger log = LoggerFactory.getLogger(OutboundMailGateway.class);

    private final MessageChannel mailOutboundChannel;

    public OutboundMailGateway(MessageChannel mailOutboundChannel) {
        this.mailOutboundChannel = mailOutboundChannel;
    }

    public void submitMail(byte[] mailContent, EmailAddress sender, List<EmailAddress> recipients) {
        MailEnvelope envelope = new MailEnvelope(
                "<" + java.util.UUID.randomUUID() + "@sealmail.local>",
                sender,
                recipients,
                "127.0.0.1",
                "submission",
                java.time.Instant.now()
        );

        log.info("Submitting outbound mail from {} to {} recipients", sender, recipients.size());

        mailOutboundChannel.send(MessageBuilder
                .withPayload(mailContent)
                .setHeader("mailEnvelope", envelope)
                .setHeader("submissionType", "api")
                .build());
    }

    @Override
    public void submitPlain(PlainOutboundMailSubmission submission) {
        submitMail(submission.mailContent(), submission.sender(), submission.recipients());
    }

    @Override
    public void submitProtected(ProtectedOutboundMailSubmission submission) {
        var builder = MessageBuilder
                .withPayload(submission.mailContent())
                .setHeader("mailEnvelope", submission.envelope())
                .setHeader("submissionType", "api")
                .setHeader("signingEnabled", submission.signingEnabled())
                .setHeader("encryptionEnabled", submission.encryptionEnabled())
                .setHeader("preferredAlgorithm", submission.preferredAlgorithm().name())
                .setHeader("recipientCertificates", submission.recipientCertificates());

        if (submission.senderCertificatePem() != null && !submission.senderCertificatePem().isBlank()) {
            builder.setHeader("senderCertificate", submission.senderCertificatePem());
        }
        if (submission.senderCertificateThumbprint() != null && !submission.senderCertificateThumbprint().isBlank()) {
            builder.setHeader("senderCertificateThumbprint", submission.senderCertificateThumbprint());
        }

        mailOutboundChannel.send(builder.build());
    }

    public void submitMail(byte[] mailContent, String sender, List<String> recipients) {
        EmailAddress senderAddr = new EmailAddress(sender);
        List<EmailAddress> recipientAddrs = recipients.stream()
                .map(EmailAddress::new)
                .toList();

        submitMail(mailContent, senderAddr, recipientAddrs);
    }
}
