package com.sealmail.domain.policy.event;

import com.sealmail.domain.shared.event.DomainEvent;

public class DomainConfigActivationChanged extends DomainEvent {

    private final String configId;
    private final boolean active;

    public DomainConfigActivationChanged(String configId, boolean active) {
        this.configId = configId;
        this.active = active;
    }

    public String getConfigId() {
        return configId;
    }

    public boolean isActive() {
        return active;
    }
}
