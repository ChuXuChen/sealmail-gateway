package com.sealmail.domain.dlp;

import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.domain.shared.model.ValueObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class DlpScanResult extends ValueObject {

    private final List<DlpViolation> violations;
    private final boolean hasViolations;
    private final DlpViolation primaryViolation;
    private final DispositionAction finalAction;
    private final String scannerName;
    private final long scanDurationMs;

    public DlpScanResult(String scannerName, List<DlpViolation> violations, long scanDurationMs) {
        if (scannerName == null || scannerName.isBlank()) {
            throw new IllegalArgumentException("Scanner name cannot be blank");
        }
        this.scannerName = scannerName;
        this.violations = violations != null ? new ArrayList<>(violations) : Collections.emptyList();
        this.hasViolations = !this.violations.isEmpty();
        this.primaryViolation = determinePrimaryViolation(this.violations);
        this.finalAction = primaryViolation != null ? primaryViolation.getAction() : DispositionAction.WARN;
        this.scanDurationMs = scanDurationMs;
    }

    private DlpViolation determinePrimaryViolation(List<DlpViolation> violations) {
        return violations.stream()
                .max(Comparator.comparingInt((DlpViolation violation) -> actionPriority(violation.getAction()))
                        .thenComparingInt(DlpViolation::getSeverity)
                        .thenComparing(DlpViolation::getRuleName))
                .orElse(null);
    }

    public static int actionPriority(DispositionAction action) {
        if (action == null) {
            return 0;
        }
        return switch (action) {
            case BLOCK -> 4;
            case QUARANTINE -> 3;
            case MUST_ENCRYPT -> 2;
            case WARN -> 1;
        };
    }

    public String getScannerName() {
        return scannerName;
    }

    public List<DlpViolation> getViolations() {
        return Collections.unmodifiableList(violations);
    }

    public boolean hasViolations() {
        return hasViolations;
    }

    public Optional<DlpViolation> getPrimaryViolation() {
        return Optional.ofNullable(primaryViolation);
    }

    public DispositionAction getFinalAction() {
        return finalAction;
    }

    public long getScanDurationMs() {
        return scanDurationMs;
    }

    public boolean shouldBlock() {
        return finalAction == DispositionAction.BLOCK;
    }

    public boolean shouldQuarantine() {
        return finalAction == DispositionAction.QUARANTINE;
    }

    public boolean shouldEncrypt() {
        return finalAction == DispositionAction.MUST_ENCRYPT;
    }

    public int getMaxSeverity() {
        return violations.stream()
                .mapToInt(DlpViolation::getSeverity)
                .max()
                .orElse(0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DlpScanResult that = (DlpScanResult) o;
        return hasViolations == that.hasViolations &&
                scanDurationMs == that.scanDurationMs &&
                scannerName.equals(that.scannerName) &&
                violations.equals(that.violations) &&
                Objects.equals(primaryViolation, that.primaryViolation) &&
                finalAction == that.finalAction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(scannerName, violations, hasViolations, primaryViolation, finalAction, scanDurationMs);
    }
}
