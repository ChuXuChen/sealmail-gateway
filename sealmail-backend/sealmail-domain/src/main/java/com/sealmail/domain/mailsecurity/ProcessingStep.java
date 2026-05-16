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
        this(id, stepName, false, false, null, Instant.now(), null);
    }

    private ProcessingStep(String id,
                           String stepName,
                           boolean completed,
                           boolean success,
                           String errorMessage,
                           Instant startedAt,
                           Instant completedAt) {
        super(id);
        this.stepName = stepName;
        this.completed = completed;
        this.success = success;
        this.errorMessage = errorMessage;
        this.startedAt = startedAt != null ? startedAt : Instant.now();
        this.completedAt = completedAt;
    }

    public static ProcessingStep rehydrate(String id,
                                           String stepName,
                                           boolean completed,
                                           boolean success,
                                           String errorMessage,
                                           Instant startedAt,
                                           Instant completedAt) {
        return new ProcessingStep(id, stepName, completed, success, errorMessage, startedAt, completedAt);
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
