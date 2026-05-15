package com.sealmail.domain.policy.event;

import com.sealmail.domain.shared.event.DomainEvent;

public class DlpPatternConfigChanged extends DomainEvent {

    private final String patternId;
    private final String operation;
    private final String name;

    public DlpPatternConfigChanged(String patternId, String operation, String name) {
        this.patternId = patternId;
        this.operation = operation;
        this.name = name;
    }

    public String getPatternId() {
        return patternId;
    }

    public String getOperation() {
        return operation;
    }

    public String getName() {
        return name;
    }
}
