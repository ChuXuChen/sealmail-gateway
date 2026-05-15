package com.sealmail.domain.policy.event;

import com.sealmail.domain.policy.PreferredAlgorithm;
import com.sealmail.domain.shared.event.DomainEvent;

public class PreferredAlgorithmChanged extends DomainEvent {

    private final String configId;
    private final PreferredAlgorithm oldAlgorithm;
    private final PreferredAlgorithm newAlgorithm;

    public PreferredAlgorithmChanged(String configId,
                                     PreferredAlgorithm oldAlgorithm,
                                     PreferredAlgorithm newAlgorithm) {
        this.configId = configId;
        this.oldAlgorithm = oldAlgorithm;
        this.newAlgorithm = newAlgorithm;
    }

    public String getConfigId() {
        return configId;
    }

    public PreferredAlgorithm getOldAlgorithm() {
        return oldAlgorithm;
    }

    public PreferredAlgorithm getNewAlgorithm() {
        return newAlgorithm;
    }
}
