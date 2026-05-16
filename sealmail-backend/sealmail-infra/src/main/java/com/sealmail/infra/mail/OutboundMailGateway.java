package com.sealmail.infra.mail;

import com.sealmail.domain.mailsecurity.CertificateSelection;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingDecision;
import com.sealmail.domain.mail.spi.OutboundMailSubmitter;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OutboundMailGateway implements OutboundMailSubmitter {

    private static final Logger log = LoggerFactory.getLogger(OutboundMailGateway.class);

    private final MessageChannel mailOutboundChannel;

    public OutboundMailGateway(@Qualifier("mailOutboundChannel") MessageChannel mailOutboundChannel) {
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

        MailProcessingContext context = MailProcessingContext.initial(
                envelope,
                MailDirection.OUTBOUND,
                "api",
                mailContent,
                null,
                envelope.getRemoteHost());
        var builder = MessageBuilder
                .withPayload(mailContent)
                .setHeader(MailProcessingHeaders.CONTEXT, context);
        mailOutboundChannel.send(builder.build());
    }

    @Override
    public void submitPlain(PlainOutboundMailSubmission submission) {
        submitMail(submission.mailContent(), submission.sender(), submission.recipients());
    }

    @Override
    public void submitProtected(ProtectedOutboundMailSubmission submission) {
        CertificateSelection certificates = CertificateSelection.empty()
                .withSenderCertificate(
                        blankToNull(submission.senderCertificatePem()),
                        blankToNull(submission.senderCertificateThumbprint()))
                .withRecipientCertificates(submission.recipientCertificates(), Map.of());
        MailProcessingDecision decision = MailProcessingDecision.none()
                .withSigningRequired(submission.signingEnabled())
                .withEncryptionRequired(submission.encryptionEnabled());
        MailProcessingContext context = MailProcessingContext.initial(
                        submission.envelope(),
                        MailDirection.OUTBOUND,
                        "api",
                        submission.mailContent(),
                        null,
                        submission.envelope().getRemoteHost())
                .withPreferredAlgorithm(submission.preferredAlgorithm())
                .withDecision(decision)
                .withCertificateSelection(certificates);
        var builder = MessageBuilder
                .withPayload(submission.mailContent())
                .setHeader(MailProcessingHeaders.CONTEXT, context);
        mailOutboundChannel.send(builder.build());
    }

    public void submitMail(byte[] mailContent, String sender, List<String> recipients) {
        EmailAddress senderAddr = new EmailAddress(sender);
        List<EmailAddress> recipientAddrs = recipients.stream()
                .map(EmailAddress::new)
                .toList();

        submitMail(mailContent, senderAddr, recipientAddrs);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
