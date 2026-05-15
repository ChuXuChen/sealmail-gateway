package com.sealmail.domain.policy.event;

import com.sealmail.domain.shared.event.DomainEvent;

public class DomainConfigDeleted extends DomainEvent {

    private final String configId;
    private final String domain;

    public DomainConfigDeleted(String configId, String domain) {
        this.configId = configId;
        this.domain = domain;
    }

    public String getConfigId() {
        return configId;
    }

    public String getDomain() {
        return domain;
    }
}
