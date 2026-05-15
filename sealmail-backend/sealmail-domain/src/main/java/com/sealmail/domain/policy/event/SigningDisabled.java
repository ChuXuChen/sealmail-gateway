package com.sealmail.domain.policy.event;

import com.sealmail.domain.shared.event.DomainEvent;

public class SigningDisabled extends DomainEvent {

    private final String configId;

    public SigningDisabled(String configId) {
        this.configId = configId;
    }

    public String getConfigId() {
        return configId;
    }
}
