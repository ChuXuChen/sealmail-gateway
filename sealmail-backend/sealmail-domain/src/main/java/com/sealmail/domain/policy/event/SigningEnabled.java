package com.sealmail.domain.policy.event;

import com.sealmail.domain.shared.event.DomainEvent;

public class SigningEnabled extends DomainEvent {

    private final String configId;

    public SigningEnabled(String configId) {
        this.configId = configId;
    }

    public String getConfigId() {
        return configId;
    }
}
