package com.sealmail.domain.mailsecurity.event;

import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.shared.event.DomainEvent;
import com.sealmail.domain.shared.model.EmailAddress;

import java.util.List;

public class MailReceived extends DomainEvent {

    private final String messageId;
    private final MailDirection direction;
    private final EmailAddress sender;
    private final List<EmailAddress> recipients;

    public MailReceived(String messageId, MailDirection direction,
                        EmailAddress sender, List<EmailAddress> recipients) {
        this.messageId = messageId;
        this.direction = direction;
        this.sender = sender;
        this.recipients = recipients;
    }

    public String getMessageId() {
        return messageId;
    }

    public MailDirection getDirection() {
        return direction;
    }

    public EmailAddress getSender() {
        return sender;
    }

    public List<EmailAddress> getRecipients() {
        return recipients;
    }
}
