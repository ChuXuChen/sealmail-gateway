package com.sealmail.domain.quarantine.event;

import com.sealmail.domain.shared.event.DomainEvent;

public class QuarantineRejected extends DomainEvent {

    private final String quarantineId;
    private final String rejectedBy;
    private final String comment;

    public QuarantineRejected(String quarantineId, String rejectedBy, String comment) {
        this.quarantineId = quarantineId;
        this.rejectedBy = rejectedBy;
        this.comment = comment;
    }

    public String getQuarantineId() {
        return quarantineId;
    }

    public String getRejectedBy() {
        return rejectedBy;
    }

    public String getComment() {
        return comment;
    }
}
