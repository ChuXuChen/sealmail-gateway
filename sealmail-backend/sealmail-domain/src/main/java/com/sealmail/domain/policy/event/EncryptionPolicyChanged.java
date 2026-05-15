package com.sealmail.domain.policy.event;

import com.sealmail.domain.policy.EncryptionPolicy;
import com.sealmail.domain.shared.event.DomainEvent;

public class EncryptionPolicyChanged extends DomainEvent {

    private final String configId;
    private final EncryptionPolicy oldPolicy;
    private final EncryptionPolicy newPolicy;

    public EncryptionPolicyChanged(String configId, EncryptionPolicy oldPolicy, EncryptionPolicy newPolicy) {
        this.configId = configId;
        this.oldPolicy = oldPolicy;
        this.newPolicy = newPolicy;
    }

    public String getConfigId() {
        return configId;
    }

    public EncryptionPolicy getOldPolicy() {
        return oldPolicy;
    }

    public EncryptionPolicy getNewPolicy() {
        return newPolicy;
    }
}
