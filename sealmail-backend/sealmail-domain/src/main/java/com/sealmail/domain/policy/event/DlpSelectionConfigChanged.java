package com.sealmail.domain.policy.event;

import com.sealmail.domain.dlp.DlpScopeType;
import com.sealmail.domain.shared.event.DomainEvent;

public class DlpSelectionConfigChanged extends DomainEvent {

    private final String selectionId;
    private final String operation;
    private final DlpScopeType scopeType;
    private final String scopeValue;

    public DlpSelectionConfigChanged(String selectionId,
                                     String operation,
                                     DlpScopeType scopeType,
                                     String scopeValue) {
        this.selectionId = selectionId;
        this.operation = operation;
        this.scopeType = scopeType;
        this.scopeValue = scopeValue;
    }

    public String getSelectionId() {
        return selectionId;
    }

    public String getOperation() {
        return operation;
    }

    public DlpScopeType getScopeType() {
        return scopeType;
    }

    public String getScopeValue() {
        return scopeValue;
    }
}
