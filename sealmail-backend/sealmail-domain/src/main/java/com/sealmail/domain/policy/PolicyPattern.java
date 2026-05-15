package com.sealmail.domain.policy;

import com.sealmail.domain.shared.model.ValueObject;

import java.util.Objects;
import java.util.regex.Pattern;

public final class PolicyPattern extends ValueObject {

    private final String name;
    private final Pattern regex;
    private final int priority;
    private final DispositionAction violationAction;

    public PolicyPattern(String name, String regex, int priority, DispositionAction violationAction) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Pattern name cannot be blank");
        }
        if (regex == null || regex.isBlank()) {
            throw new IllegalArgumentException("Regex cannot be blank");
        }
        this.name = name;
        this.regex = Pattern.compile(regex);
        this.priority = priority;
        this.violationAction = violationAction;
    }

    public boolean matches(String content) {
        return content != null && regex.matcher(content).find();
    }

    public String getName() {
        return name;
    }

    public String getRegex() {
        return regex.pattern();
    }

    public int getPriority() {
        return priority;
    }

    public DispositionAction getViolationAction() {
        return violationAction;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PolicyPattern that = (PolicyPattern) o;
        return priority == that.priority && name.equals(that.name) &&
                regex.pattern().equals(that.regex.pattern()) && violationAction == that.violationAction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, regex.pattern(), priority, violationAction);
    }
}
