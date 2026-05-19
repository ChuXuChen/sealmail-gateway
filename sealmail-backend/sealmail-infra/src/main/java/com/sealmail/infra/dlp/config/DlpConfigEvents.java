package com.sealmail.infra.dlp.config;

import com.sealmail.domain.dlp.DlpScopeType;
import com.sealmail.domain.policy.event.DlpPatternConfigChanged;
import com.sealmail.domain.policy.event.DlpSelectionConfigChanged;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.DlpPatternEntity;
import com.sealmail.infra.persistence.entity.DlpSelectionEntity;
import org.springframework.stereotype.Component;

@Component
class DlpConfigEvents {

    private final DomainEventPublisher domainEventPublisher;

    DlpConfigEvents(DomainEventPublisher domainEventPublisher) {
        this.domainEventPublisher = domainEventPublisher;
    }

    void publishPatternChanged(DlpPatternEntity entity, String operation) {
        domainEventPublisher.publishEvent(new DlpPatternConfigChanged(
                entity.getId(),
                operation,
                entity.getName()));
    }

    void publishSelectionChanged(DlpSelectionEntity entity, String operation) {
        domainEventPublisher.publishEvent(new DlpSelectionConfigChanged(
                entity.getId(),
                operation,
                DlpScopeType.valueOf(entity.getScopeType()),
                entity.getScopeValue()));
    }
}
