package com.sealmail.domain.dlp.config;

import com.sealmail.domain.dlp.DlpContentKind;
import com.sealmail.domain.dlp.DlpMaskingStrategy;
import com.sealmail.domain.dlp.DlpPolicyMode;
import com.sealmail.domain.dlp.DlpRuleType;
import com.sealmail.domain.dlp.DlpScopeType;
import com.sealmail.domain.mailsecurity.MailDirection;
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

    List<DlpRuleSettings> listRuleSettings();

    DlpRuleSettings createRule(DlpRuleSettingsUpdate update);

    DlpRuleSettings updateRule(String id, DlpRuleSettingsUpdate update);

    void deleteRule(String id);

    List<DlpRuleGroupSettings> listRuleGroupSettings();

    DlpRuleGroupSettings createRuleGroup(DlpRuleGroupSettingsUpdate update);

    DlpRuleGroupSettings updateRuleGroup(String id, DlpRuleGroupSettingsUpdate update);

    void deleteRuleGroup(String id);

    List<DlpPolicySettings> listPolicySettings();

    DlpPolicySettings createPolicy(DlpPolicySettingsUpdate update);

    DlpPolicySettings updatePolicy(String id, DlpPolicySettingsUpdate update);

    void deletePolicy(String id);

    record DlpPatternSettings(
            String id,
            String name,
            String description,
            String regex,
            DlpRuleType type,
            String builtinCode,
            List<DlpContentKind> contentKinds,
            int minMatchCount,
            int maxEvidenceCount,
            DlpMaskingStrategy maskingStrategy,
            DispositionAction action,
            int severity,
            int priority,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
        public DlpPatternSettings {
            contentKinds = contentKinds == null ? List.of() : List.copyOf(contentKinds);
        }
    }

    record DlpPatternSettingsUpdate(
            String name,
            String description,
            String regex,
            String type,
            String builtinCode,
            List<String> contentKinds,
            Integer minMatchCount,
            Integer maxEvidenceCount,
            String maskingStrategy,
            String action,
            Integer severity,
            Integer priority,
            Boolean enabled
    ) {
        public DlpPatternSettingsUpdate {
            contentKinds = contentKinds == null ? null : List.copyOf(contentKinds);
        }

        public DlpPatternSettingsUpdate(String name,
                                        String description,
                                        String regex,
                                        String action,
                                        Integer severity,
                                        Integer priority,
                                        Boolean enabled) {
            this(name, description, regex, null, null, null, null, null, null,
                    action, severity, priority, enabled);
        }
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

    record DlpRuleSettings(
            String id,
            String name,
            String description,
            DlpRuleType type,
            String pattern,
            String builtinCode,
            List<DlpContentKind> contentKinds,
            int minMatchCount,
            int maxEvidenceCount,
            DlpMaskingStrategy maskingStrategy,
            DispositionAction action,
            int severity,
            int priority,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
        public DlpRuleSettings {
            contentKinds = contentKinds == null ? List.of() : List.copyOf(contentKinds);
        }
    }

    record DlpRuleSettingsUpdate(
            String name,
            String description,
            String type,
            String pattern,
            String builtinCode,
            List<String> contentKinds,
            Integer minMatchCount,
            Integer maxEvidenceCount,
            String maskingStrategy,
            String action,
            Integer severity,
            Integer priority,
            Boolean enabled
    ) {
        public DlpRuleSettingsUpdate {
            contentKinds = contentKinds == null ? null : List.copyOf(contentKinds);
        }
    }

    record DlpRuleGroupSettings(
            String id,
            String name,
            String description,
            boolean enabled,
            int priority,
            List<String> ruleIds,
            Instant createdAt,
            Instant updatedAt
    ) {
        public DlpRuleGroupSettings {
            ruleIds = ruleIds == null ? List.of() : List.copyOf(ruleIds);
        }
    }

    record DlpRuleGroupSettingsUpdate(
            String name,
            String description,
            Boolean enabled,
            Integer priority,
            List<String> ruleIds
    ) {
        public DlpRuleGroupSettingsUpdate {
            ruleIds = ruleIds == null ? null : List.copyOf(ruleIds);
        }
    }

    record DlpPolicySettings(
            String id,
            String name,
            String description,
            DlpPolicyMode mode,
            MailDirection direction,
            List<String> senderDomains,
            List<String> recipientDomains,
            List<String> senderAddressPatterns,
            List<String> recipientAddressPatterns,
            boolean attachmentRequired,
            boolean enabled,
            int priority,
            List<String> ruleGroupIds,
            Instant createdAt,
            Instant updatedAt
    ) {
        public DlpPolicySettings {
            senderDomains = senderDomains == null ? List.of() : List.copyOf(senderDomains);
            recipientDomains = recipientDomains == null ? List.of() : List.copyOf(recipientDomains);
            senderAddressPatterns = senderAddressPatterns == null ? List.of() : List.copyOf(senderAddressPatterns);
            recipientAddressPatterns = recipientAddressPatterns == null ? List.of() : List.copyOf(recipientAddressPatterns);
            ruleGroupIds = ruleGroupIds == null ? List.of() : List.copyOf(ruleGroupIds);
        }
    }

    record DlpPolicySettingsUpdate(
            String name,
            String description,
            String mode,
            String direction,
            List<String> senderDomains,
            List<String> recipientDomains,
            List<String> senderAddressPatterns,
            List<String> recipientAddressPatterns,
            Boolean attachmentRequired,
            Boolean enabled,
            Integer priority,
            List<String> ruleGroupIds
    ) {
        public DlpPolicySettingsUpdate {
            senderDomains = senderDomains == null ? null : List.copyOf(senderDomains);
            recipientDomains = recipientDomains == null ? null : List.copyOf(recipientDomains);
            senderAddressPatterns = senderAddressPatterns == null ? null : List.copyOf(senderAddressPatterns);
            recipientAddressPatterns = recipientAddressPatterns == null ? null : List.copyOf(recipientAddressPatterns);
            ruleGroupIds = ruleGroupIds == null ? null : List.copyOf(ruleGroupIds);
        }
    }
}
