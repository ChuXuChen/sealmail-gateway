package com.sealmail.domain.shared.repository;

import com.sealmail.domain.shared.event.DomainEvent;

import java.util.List;

public interface DomainEventRepository {

    void save(DomainEvent event);

    List<DomainEvent> findByAggregateId(String aggregateId);

    List<DomainEvent> findAll();
}
