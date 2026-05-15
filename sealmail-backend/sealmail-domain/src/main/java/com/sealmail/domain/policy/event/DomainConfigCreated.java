package com.sealmail.domain.policy.event;

import com.sealmail.domain.shared.event.DomainEvent;

public class DomainConfigCreated extends DomainEvent {

    private final String configId;
    private final String domain;
    private final boolean local;

    public DomainConfigCreated(String configId, String domain, boolean local) {
        this.configId = configId;
        this.domain = domain;
        this.local = local;
    }

    public String getConfigId() {
        return configId;
    }

    public String getDomain() {
        return domain;
    }

    public boolean isLocal() {
        return local;
    }
}
