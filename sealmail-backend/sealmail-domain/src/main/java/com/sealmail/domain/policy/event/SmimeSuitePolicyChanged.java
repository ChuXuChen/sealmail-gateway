package com.sealmail.domain.policy.event;

import com.sealmail.domain.shared.event.DomainEvent;

import java.util.List;

public class SmimeSuitePolicyChanged extends DomainEvent {

    private final String configId;
    private final List<String> changedFields;

    public SmimeSuitePolicyChanged(String configId, List<String> changedFields) {
        this.configId = configId;
        this.changedFields = changedFields != null ? List.copyOf(changedFields) : List.of();
    }

    public String getConfigId() {
        return configId;
    }

    public List<String> getChangedFields() {
        return changedFields;
    }
}
