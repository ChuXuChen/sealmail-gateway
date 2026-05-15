package com.sealmail.domain.mailsecurity.event;

import com.sealmail.domain.shared.event.DomainEvent;
import com.sealmail.domain.shared.model.EmailAddress;

public class MailDecrypted extends DomainEvent {

    private final String messageId;
    private final EmailAddress recipient;

    public MailDecrypted(String messageId, EmailAddress recipient) {
        this.messageId = messageId;
        this.recipient = recipient;
    }

    public String getMessageId() {
        return messageId;
    }

    public EmailAddress getRecipient() {
        return recipient;
    }
}
