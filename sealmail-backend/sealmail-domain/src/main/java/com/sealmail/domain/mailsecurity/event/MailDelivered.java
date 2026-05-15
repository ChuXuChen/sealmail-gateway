package com.sealmail.domain.mailsecurity.event;

import com.sealmail.domain.shared.event.DomainEvent;

public class MailDelivered extends DomainEvent {

    private final String messageId;

    public MailDelivered(String messageId) {
        this.messageId = messageId;
    }

    public String getMessageId() {
        return messageId;
    }
}
