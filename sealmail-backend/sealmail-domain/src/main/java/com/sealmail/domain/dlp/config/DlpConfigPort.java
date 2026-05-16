package com.sealmail.domain.dlp.config;

import com.sealmail.domain.dlp.DlpScopeType;
import com.sealmail.domain.policy.DispositionAction;

import java.time.Instant;
import java.util.List;

public interface DlpConfigPort {

    List<DlpPatternSettings> listPatternSettings();

    DlpPatternSettings createPattern(DlpPatternSettingsUpdate update);

    DlpPatternSettings updatePattern(String id, DlpPatternSettingsUpdate update);

    void deletePattern(String id);

    List<DlpSelectionSettings> listSelectionSettings();

    DlpSelectionSettings createSelection(DlpSelectionSettingsUpdate update);

    DlpSelectionSettings updateSelection(String id, DlpSelectionSettingsUpdate update);

    void deleteSelection(String id);

    record DlpPatternSettings(
            String id,
            String name,
            String description,
            String regex,
            DispositionAction action,
            int severity,
            int priority,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    record DlpPatternSettingsUpdate(
            String name,
            String description,
            String regex,
            String action,
            Integer severity,
            Integer priority,
            Boolean enabled
    ) {
    }

    record DlpSelectionSettings(
            String id,
            DlpScopeType scopeType,
            String scopeValue,
            List<String> patternIds,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
        public DlpSelectionSettings {
            patternIds = patternIds == null ? null : List.copyOf(patternIds);
        }
    }

    record DlpSelectionSettingsUpdate(
            String scopeType,
            String scopeValue,
            List<String> patternIds,
            String patternMode,
            Boolean enabled
    ) {
        public DlpSelectionSettingsUpdate {
            patternIds = patternIds == null ? null : List.copyOf(patternIds);
        }
    }
}
