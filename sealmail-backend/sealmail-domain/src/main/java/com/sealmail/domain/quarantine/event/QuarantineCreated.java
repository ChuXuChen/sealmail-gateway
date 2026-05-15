package com.sealmail.domain.quarantine.event;

import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.shared.event.DomainEvent;
import com.sealmail.domain.shared.model.EmailAddress;

public class QuarantineCreated extends DomainEvent {

    private final String quarantineId;
    private final String messageId;
    private final EmailAddress sender;
    private final QuarantineReason reason;

    public QuarantineCreated(String quarantineId, String messageId, EmailAddress sender, QuarantineReason reason) {
        this.quarantineId = quarantineId;
        this.messageId = messageId;
        this.sender = sender;
        this.reason = reason;
    }

    public String getQuarantineId() {
        return quarantineId;
    }

    public String getMessageId() {
        return messageId;
    }

    public EmailAddress getSender() {
        return sender;
    }

    public QuarantineReason getReason() {
        return reason;
    }
}
