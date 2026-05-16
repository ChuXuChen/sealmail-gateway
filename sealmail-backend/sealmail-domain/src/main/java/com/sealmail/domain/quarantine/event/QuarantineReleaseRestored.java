package com.sealmail.domain.quarantine.event;

import com.sealmail.domain.shared.event.DomainEvent;

public class QuarantineReleaseRestored extends DomainEvent {

    private final String quarantineId;
    private final String restoredBy;
    private final String comment;

    public QuarantineReleaseRestored(String quarantineId, String restoredBy, String comment) {
        this.quarantineId = quarantineId;
        this.restoredBy = restoredBy;
        this.comment = comment;
    }

    public String getQuarantineId() {
        return quarantineId;
    }

    public String getRestoredBy() {
        return restoredBy;
    }

    public String getComment() {
        return comment;
    }
}
