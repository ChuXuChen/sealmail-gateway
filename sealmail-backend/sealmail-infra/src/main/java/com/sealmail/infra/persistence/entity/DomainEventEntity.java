package com.sealmail.infra.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "domain_event")
public class DomainEventEntity {

    @Id
    @Column(name = "event_id", length = 128)
    private String eventId;

    @Column(name = "aggregate_id", nullable = false, length = 254)
    private String aggregateId;

    @Column(name = "aggregate_type", nullable = false, length = 128)
    private String aggregateType;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "event_data", nullable = false, length = 8192)
    private String eventData;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
