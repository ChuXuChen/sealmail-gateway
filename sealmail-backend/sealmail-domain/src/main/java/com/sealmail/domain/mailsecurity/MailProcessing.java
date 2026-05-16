package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.mailsecurity.event.MailDelivered;
import com.sealmail.domain.mailsecurity.event.MailQuarantined;
import com.sealmail.domain.mailsecurity.event.MailReceived;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.shared.model.AggregateRoot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MailProcessing extends AggregateRoot<String> {

    private final MailEnvelope envelope;
    private final MailDirection direction;
    private RoutingDecision routingDecision;
    private ProcessingResult result;
    private final List<ProcessingStep> steps;

    private MailProcessing(String id, MailEnvelope envelope, MailDirection direction) {
        super(id);
        this.envelope = envelope;
        this.direction = direction;
        this.steps = new ArrayList<>();
    }

    public static MailProcessing create(MailEnvelope envelope, MailDirection direction) {
        return create(UUID.randomUUID().toString(), envelope, direction);
    }

    public static MailProcessing create(String id, MailEnvelope envelope, MailDirection direction) {
        if (envelope == null) {
            throw new IllegalArgumentException("Mail envelope cannot be null");
        }
        if (direction == null) {
            throw new IllegalArgumentException("Mail direction cannot be null");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Mail processing id cannot be blank");
        }
        MailProcessing processing = new MailProcessing(id, envelope, direction);
        processing.registerEvent(new MailReceived(
                envelope.getMessageId(),
                direction,
                envelope.getSender(),
                envelope.getRecipients()
        ));
        return processing;
    }

    public void setRoutingDecision(RoutingDecision decision) {
        if (decision == null) {
            throw new IllegalArgumentException("Routing decision cannot be null");
        }
        this.routingDecision = decision;
    }

    public void addStep(String stepName) {
        ProcessingStep step = new ProcessingStep(UUID.randomUUID().toString(), stepName);
        steps.add(step);
    }

    public void restoreStep(ProcessingStep step) {
        if (step == null) {
            return;
        }
        steps.add(step);
    }

    public void completeStep(String stepName, boolean success, String errorMessage) {
        steps.stream()
                .filter(s -> s.getStepName().equals(stepName) && !s.isCompleted())
                .findFirst()
                .ifPresent(s -> s.complete(success, errorMessage));
    }

    public void completeProcessing(ProcessingResult result) {
        this.result = result;
        if (result == ProcessingResult.SUCCESS) {
            registerEvent(new MailDelivered(envelope.getMessageId()));
        }
    }

    public void quarantine(QuarantineReason reason, String detail) {
        this.result = ProcessingResult.FAILED;
        registerEvent(new MailQuarantined(envelope.getMessageId(), reason, detail));
    }

    public MailEnvelope getEnvelope() {
        return envelope;
    }

    public MailDirection getDirection() {
        return direction;
    }

    public RoutingDecision getRoutingDecision() {
        return routingDecision;
    }

    public ProcessingResult getResult() {
        return result;
    }

    public List<ProcessingStep> getSteps() {
        return new ArrayList<>(steps);
    }
}
