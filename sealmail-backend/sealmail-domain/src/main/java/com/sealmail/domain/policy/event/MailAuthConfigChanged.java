package com.sealmail.domain.policy.event;

import com.sealmail.domain.shared.event.DomainEvent;

import java.util.List;

public class MailAuthConfigChanged extends DomainEvent {

    private final String configId;
    private final List<String> changedSections;

    public MailAuthConfigChanged(String configId, List<String> changedSections) {
        this.configId = configId;
        this.changedSections = changedSections != null ? List.copyOf(changedSections) : List.of();
    }

    public String getConfigId() {
        return configId;
    }

    public List<String> getChangedSections() {
        return changedSections;
    }
}
