package com.sealmail.edge.metrics;

import java.time.Instant;
import java.util.concurrent.atomic.LongAdder;

public final class EdgeMetrics {
    private final Instant startedAt = Instant.now();
    private final LongAdder acceptedConnections = new LongAdder();
    private final LongAdder rejectedConnections = new LongAdder();
    private final LongAdder completedSessions = new LongAdder();
    private final LongAdder relayedMessages = new LongAdder();
    private final LongAdder failedMessages = new LongAdder();
    private final LongAdder temporaryFailures = new LongAdder();
    private final LongAdder permanentFailures = new LongAdder();
    private final LongAdder oversizedMessages = new LongAdder();
    private final LongAdder relayedBytes = new LongAdder();

    public void connectionAccepted() {
        acceptedConnections.increment();
    }

    public void connectionRejected() {
        rejectedConnections.increment();
    }

    public void sessionCompleted() {
        completedSessions.increment();
    }

    public void messageRelayed(long bytes) {
        relayedMessages.increment();
        relayedBytes.add(bytes);
    }

    public void messageFailed(int replyCode) {
        failedMessages.increment();
        if (replyCode >= 400 && replyCode < 500) {
            temporaryFailures.increment();
        } else if (replyCode >= 500 && replyCode < 600) {
            permanentFailures.increment();
        }
    }

    public void oversizedMessage() {
        oversizedMessages.increment();
        failedMessages.increment();
        permanentFailures.increment();
    }

    public Snapshot snapshot() {
        return new Snapshot(
                startedAt,
                acceptedConnections.sum(),
                rejectedConnections.sum(),
                completedSessions.sum(),
                relayedMessages.sum(),
                failedMessages.sum(),
                temporaryFailures.sum(),
                permanentFailures.sum(),
                oversizedMessages.sum(),
                relayedBytes.sum());
    }

    public record Snapshot(
            Instant startedAt,
            long acceptedConnections,
            long rejectedConnections,
            long completedSessions,
            long relayedMessages,
            long failedMessages,
            long temporaryFailures,
            long permanentFailures,
            long oversizedMessages,
            long relayedBytes
    ) {
    }
}
