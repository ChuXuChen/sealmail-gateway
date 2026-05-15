package com.sealmail.domain.mailsecurity.event;

import com.sealmail.domain.shared.event.DomainEvent;
import com.sealmail.domain.shared.model.EmailAddress;

public class MailVerified extends DomainEvent {

    private final String messageId;
    private final EmailAddress sender;
    private final boolean valid;

    public MailVerified(String messageId, EmailAddress sender, boolean valid) {
        this.messageId = messageId;
        this.sender = sender;
        this.valid = valid;
    }

    public String getMessageId() {
        return messageId;
    }

    public EmailAddress getSender() {
        return sender;
    }

    public boolean isValid() {
        return valid;
    }
}
