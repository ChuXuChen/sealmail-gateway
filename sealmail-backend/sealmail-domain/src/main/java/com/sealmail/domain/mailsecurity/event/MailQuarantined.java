package com.sealmail.domain.mailsecurity.event;

import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.shared.event.DomainEvent;

public class MailQuarantined extends DomainEvent {

    private final String messageId;
    private final QuarantineReason reason;
    private final String detail;

    public MailQuarantined(String messageId, QuarantineReason reason, String detail) {
        this.messageId = messageId;
        this.reason = reason;
        this.detail = detail;
    }

    public String getMessageId() {
        return messageId;
    }

    public QuarantineReason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }
}
