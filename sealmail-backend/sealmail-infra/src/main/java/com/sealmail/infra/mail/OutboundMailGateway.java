package com.sealmail.infra.mail;

import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.shared.model.EmailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboundMailGateway {

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

    public void submitMail(byte[] mailContent, String sender, List<String> recipients) {
        EmailAddress senderAddr = new EmailAddress(sender);
        List<EmailAddress> recipientAddrs = recipients.stream()
                .map(EmailAddress::new)
                .toList();

        submitMail(mailContent, senderAddr, recipientAddrs);
    }
}
