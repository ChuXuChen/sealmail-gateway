package com.sealmail.infra.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "mail_processing")
public class MailProcessingEntity {

    @Id
    @Column(name = "id", length = 128)
    private String id;

    @Column(name = "message_id", nullable = false, length = 254)
    private String messageId;

    @Column(name = "direction", nullable = false, length = 16)
    private String direction;

    @Column(name = "sender_email", nullable = false, length = 254)
    private String senderEmail;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "mail_processing_recipient", joinColumns = @JoinColumn(name = "mail_processing_id"))
    @OrderColumn(name = "position")
    @Column(name = "recipient_email", nullable = false, length = 254)
    private List<String> recipients = new ArrayList<>();

    @Column(name = "remote_host", length = 254)
    private String remoteHost;

    @Column(name = "helo", length = 254)
    private String helo;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "routing_decision", length = 2048)
    private String routingDecision;

    @Column(name = "result", length = 16)
    private String result;

    // Steps are managed via native SQL queries, no JPA association needed

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private long version;
}
