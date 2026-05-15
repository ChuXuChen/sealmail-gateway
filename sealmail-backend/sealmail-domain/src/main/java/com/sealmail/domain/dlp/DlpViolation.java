package com.sealmail.domain.dlp;

import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.domain.shared.model.ValueObject;

import java.util.Objects;

public final class DlpViolation extends ValueObject {

    private final String ruleName;
    private final String description;
    private final String matchedContent;
    private final int severity;
    private final DispositionAction action;

    public DlpViolation(String ruleName, String description, String matchedContent,
                        int severity, DispositionAction action) {
        if (ruleName == null || ruleName.isBlank()) {
            throw new IllegalArgumentException("Rule name cannot be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description cannot be blank");
        }
        if (severity < 1 || severity > 10) {
            throw new IllegalArgumentException("Severity must be between 1 and 10");
        }
        this.ruleName = ruleName;
        this.description = description;
        this.matchedContent = matchedContent != null ? matchedContent : "";
        this.severity = severity;
        this.action = action != null ? action : DispositionAction.WARN;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getDescription() {
        return description;
    }

    public String getMatchedContent() {
        return matchedContent;
    }

    public int getSeverity() {
        return severity;
    }

    public DispositionAction getAction() {
        return action;
    }

    public boolean isBlocker() {
        return action == DispositionAction.BLOCK || action == DispositionAction.QUARANTINE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DlpViolation that = (DlpViolation) o;
        return severity == that.severity && ruleName.equals(that.ruleName) &&
                description.equals(that.description) && action == that.action;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ruleName, description, severity, action);
    }
}
