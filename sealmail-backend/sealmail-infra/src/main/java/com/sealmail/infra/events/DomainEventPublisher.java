package com.sealmail.infra.events;

import com.sealmail.domain.shared.event.DomainEvent;
import com.sealmail.domain.shared.repository.DomainEventRepository;
import com.sealmail.domain.shared.model.AggregateRoot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;
    private final DomainEventRepository domainEventRepository;

    public DomainEventPublisher(ApplicationEventPublisher applicationEventPublisher,
                                DomainEventRepository domainEventRepository) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.domainEventRepository = domainEventRepository;
    }

    @Transactional
    public void publishEvent(DomainEvent event) {
        domainEventRepository.save(event);
        applicationEventPublisher.publishEvent(event);
        log.debug("Saved event to outbox: {}", event.getClass().getSimpleName());
    }

    @Transactional
    public void publishEvents(AggregateRoot<?> aggregate) {
        List<DomainEvent> events = aggregate.getDomainEvents();
        if (events.isEmpty()) {
            return;
        }

        for (DomainEvent event : events) {
            publishEvent(event);
            log.debug("Saved event to outbox: {} for aggregate {}",
                    event.getClass().getSimpleName(), aggregate.getId());
        }

        // 2. Clear events from aggregate
        aggregate.clearDomainEvents();
    }

    @Transactional
    public void publishEvents(List<? extends AggregateRoot<?>> aggregates) {
        for (AggregateRoot<?> aggregate : aggregates) {
            publishEvents(aggregate);
        }
    }
}
