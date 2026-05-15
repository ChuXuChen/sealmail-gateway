package com.sealmail.domain.quarantine.event;

import com.sealmail.domain.shared.event.DomainEvent;

public class QuarantineReleased extends DomainEvent {

    private final String quarantineId;
    private final String releasedBy;
    private final String comment;

    public QuarantineReleased(String quarantineId, String releasedBy, String comment) {
        this.quarantineId = quarantineId;
        this.releasedBy = releasedBy;
        this.comment = comment;
    }

    public String getQuarantineId() {
        return quarantineId;
    }

    public String getReleasedBy() {
        return releasedBy;
    }

    public String getComment() {
        return comment;
    }
}
