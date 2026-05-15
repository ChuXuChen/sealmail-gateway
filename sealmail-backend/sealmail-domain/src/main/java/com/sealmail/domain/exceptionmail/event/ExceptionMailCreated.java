package com.sealmail.domain.exceptionmail.event;

import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.shared.event.DomainEvent;

public class ExceptionMailCreated extends DomainEvent {

    private final String exceptionMailId;
    private final String messageId;
    private final QuarantineReason reason;
    private final String detail;

    public ExceptionMailCreated(String exceptionMailId,
                                String messageId,
                                QuarantineReason reason,
                                String detail) {
        this.exceptionMailId = exceptionMailId;
        this.messageId = messageId;
        this.reason = reason;
        this.detail = detail;
    }

    public String getExceptionMailId() {
        return exceptionMailId;
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
