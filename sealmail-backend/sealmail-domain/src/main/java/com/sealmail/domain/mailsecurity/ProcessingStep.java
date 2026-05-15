package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.shared.model.Entity;

import java.time.Instant;

public class ProcessingStep extends Entity<String> {

    private final String stepName;
    private boolean completed;
    private boolean success;
    private String errorMessage;
    private final Instant startedAt;
    private Instant completedAt;

    public ProcessingStep(String id, String stepName) {
        super(id);
        this.stepName = stepName;
        this.startedAt = Instant.now();
        this.completed = false;
    }

    public void complete(boolean success, String errorMessage) {
        this.completed = true;
        this.success = success;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
    }

    public String getStepName() {
        return stepName;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
