package com.sealmail.domain.policy.event;

import com.sealmail.domain.shared.event.DomainEvent;

public class DkimSettingChanged extends DomainEvent {

    private final String configId;
    private final boolean enabled;

    public DkimSettingChanged(String configId, boolean enabled) {
        this.configId = configId;
        this.enabled = enabled;
    }

    public String getConfigId() {
        return configId;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
