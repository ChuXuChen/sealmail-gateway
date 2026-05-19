package com.sealmail.infra.dlp.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.dlp.DlpContentKind;
import com.sealmail.domain.dlp.DlpMaskingStrategy;
import com.sealmail.domain.dlp.DlpPolicy;
import com.sealmail.domain.dlp.DlpPolicyMode;
import com.sealmail.domain.dlp.DlpRule;
import com.sealmail.domain.dlp.DlpRuleGroup;
import com.sealmail.domain.dlp.DlpRuleType;
import com.sealmail.domain.dlp.DlpScopeType;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpEdmDatasetSettings;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpFingerprintLibrarySettings;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpPatternSettings;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpPatternSettingsUpdate;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpPolicySettings;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpRuleSettings;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpRuleSettingsUpdate;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpSelectionSettings;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpSelectionSettingsUpdate;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.infra.persistence.entity.DlpEdmDatasetEntity;
import com.sealmail.infra.persistence.entity.DlpFingerprintLibraryEntity;
import com.sealmail.infra.persistence.entity.DlpPatternEntity;
import com.sealmail.infra.persistence.entity.DlpPolicyEntity;
import com.sealmail.infra.persistence.entity.DlpRuleGroupEntity;
import com.sealmail.infra.persistence.entity.DlpSelectionEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class DlpConfigMapper {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    DlpConfigMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    DlpPatternConfig toPatternConfig(DlpPatternEntity entity) {
        DlpRuleType storedType = DlpConfigValidation.parseEnumOrDefault(
                DlpRuleType.class,
                entity.getRuleType(),
                DlpRuleType.PATTERN,
                "DLP rule type 无效");
        String pattern = entity.getRegex();
        String builtinCode = entity.getBuiltinCode();
        DlpRuleType type = DlpConfigValidation.canonicalType(storedType);
        if (storedType == DlpRuleType.KEYWORD) {
            pattern = DlpConfigValidation.keywordRegex(pattern);
        } else if (storedType == DlpRuleType.BUILTIN) {
            pattern = DlpConfigValidation.builtinRegex(builtinCode);
        }
        return new DlpPatternConfig(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                pattern,
                type,
                builtinCode,
                parseContentKinds(entity.getContentKinds()),
                entity.getMinMatchCount() > 0 ? entity.getMinMatchCount() : 1,
                entity.getMaxEvidenceCount() > 0 ? entity.getMaxEvidenceCount() : 5,
                DlpConfigValidation.parseEnumOrDefault(
                        DlpMaskingStrategy.class,
                        entity.getMaskingStrategy(),
                        DlpMaskingStrategy.DEFAULT,
                        "DLP masking strategy 无效"),
                DispositionAction.valueOf(entity.getAction()),
                entity.getSeverity(),
                entity.getPriority(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    DlpPatternSettings toPatternSettings(DlpPatternConfig config) {
        return new DlpPatternSettings(
                config.id(),
                config.name(),
                config.description(),
                config.regex(),
                config.type(),
                config.builtinCode(),
                config.contentKinds(),
                config.minMatchCount(),
                config.maxEvidenceCount(),
                config.maskingStrategy(),
                config.action(),
                config.severity(),
                config.priority(),
                config.enabled(),
                config.createdAt(),
                config.updatedAt()
        );
    }

    DlpPatternUpdate toPatternUpdate(DlpPatternSettingsUpdate update) {
        return new DlpPatternUpdate(
                update.name(),
                update.description(),
                update.regex(),
                update.type(),
                update.builtinCode(),
                update.contentKinds(),
                update.minMatchCount(),
                update.maxEvidenceCount(),
                update.maskingStrategy(),
                update.action(),
                update.severity(),
                update.priority(),
                update.enabled()
        );
    }

    DlpPatternUpdate toPatternUpdate(DlpRuleSettingsUpdate update) {
        return new DlpPatternUpdate(
                update.name(),
                update.description(),
                update.pattern(),
                update.type(),
                update.builtinCode(),
                update.contentKinds(),
                update.minMatchCount(),
                update.maxEvidenceCount(),
                update.maskingStrategy(),
                update.action(),
                update.severity(),
                update.priority(),
                update.enabled()
        );
    }

    DlpRule toRule(DlpPatternConfig config) {
        return new DlpRule(
                config.id(),
                config.name(),
                config.description(),
                config.type(),
                config.regex(),
                config.builtinCode(),
                config.contentKinds(),
                config.minMatchCount(),
                config.maxEvidenceCount(),
                config.maskingStrategy(),
                config.priority(),
                config.action(),
                config.severity(),
                config.enabled(),
                config.createdAt(),
                config.updatedAt()
        );
    }

    DlpRuleSettings toRuleSettings(DlpPatternConfig config) {
        return new DlpRuleSettings(
                config.id(),
                config.name(),
                config.description(),
                config.type(),
                config.regex(),
                config.builtinCode(),
                config.contentKinds(),
                config.minMatchCount(),
                config.maxEvidenceCount(),
                config.maskingStrategy(),
                config.action(),
                config.severity(),
                config.priority(),
                config.enabled(),
                config.createdAt(),
                config.updatedAt()
        );
    }

    DlpRuleSettings toRuleSettings(DlpRule rule) {
        return new DlpRuleSettings(
                rule.id(),
                rule.name(),
                rule.description(),
                rule.type(),
                rule.pattern(),
                rule.builtinCode(),
                rule.contentKinds(),
                rule.minMatchCount(),
                rule.maxEvidenceCount(),
                rule.maskingStrategy(),
                rule.defaultAction(),
                rule.severity(),
                rule.priority(),
                rule.enabled(),
                rule.createdAt(),
                rule.updatedAt()
        );
    }

    DlpSelectionConfig toSelectionConfig(DlpSelectionEntity entity) {
        return new DlpSelectionConfig(
                entity.getId(),
                DlpScopeType.valueOf(entity.getScopeType()),
                entity.getScopeValue(),
                entity.isAllPatterns() ? null : List.copyOf(entity.getPatternIds()),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    DlpSelectionSettings toSelectionSettings(DlpSelectionConfig config) {
        return new DlpSelectionSettings(
                config.id(),
                config.scopeType(),
                config.scopeValue(),
                config.patternIds(),
                config.enabled(),
                config.createdAt(),
                config.updatedAt()
        );
    }

    DlpSelectionUpdate toSelectionUpdate(DlpSelectionSettingsUpdate update) {
        return new DlpSelectionUpdate(
                update.scopeType(),
                update.scopeValue(),
                update.patternIds(),
                update.patternMode(),
                update.enabled()
        );
    }

    DlpRuleGroup toRuleGroup(DlpRuleGroupEntity entity, List<String> ruleIds) {
        return new DlpRuleGroup(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.isEnabled(),
                entity.getPriority(),
                ruleIds,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    com.sealmail.domain.dlp.config.DlpConfigPort.DlpRuleGroupSettings toRuleGroupSettings(DlpRuleGroup group) {
        return new com.sealmail.domain.dlp.config.DlpConfigPort.DlpRuleGroupSettings(
                group.id(),
                group.name(),
                group.description(),
                group.enabled(),
                group.priority(),
                group.ruleIds(),
                group.createdAt(),
                group.updatedAt()
        );
    }

    DlpPolicy toPolicy(DlpPolicyEntity entity, List<String> ruleGroupIds) {
        return new DlpPolicy(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                DlpConfigValidation.parseEnumOrDefault(
                        DlpPolicyMode.class,
                        entity.getMode(),
                        DlpPolicyMode.ENFORCE,
                        "DLP policy mode 无效"),
                DlpConfigValidation.nullableEnum(MailDirection.class, entity.getDirection()),
                List.copyOf(entity.getSenderDomains()),
                List.copyOf(entity.getRecipientDomains()),
                List.copyOf(entity.getSenderAddressPatterns()),
                List.copyOf(entity.getRecipientAddressPatterns()),
                entity.isAttachmentRequired(),
                entity.isEnabled(),
                entity.getPriority(),
                ruleGroupIds,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    DlpPolicySettings toPolicySettings(DlpPolicy policy) {
        return new DlpPolicySettings(
                policy.id(),
                policy.name(),
                policy.description(),
                policy.mode(),
                policy.direction(),
                policy.senderDomains(),
                policy.recipientDomains(),
                policy.senderAddressPatterns(),
                policy.recipientAddressPatterns(),
                policy.attachmentRequired(),
                policy.enabled(),
                policy.priority(),
                policy.ruleGroupIds(),
                policy.createdAt(),
                policy.updatedAt()
        );
    }

    DlpEdmDatasetSettings toEdmDatasetSettings(DlpEdmDatasetEntity entity) {
        return new DlpEdmDatasetSettings(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.isEnabled(),
                entity.getValueCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    DlpFingerprintLibrarySettings toFingerprintLibrarySettings(DlpFingerprintLibraryEntity entity) {
        return new DlpFingerprintLibrarySettings(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.isEnabled(),
                entity.getDocumentCount(),
                entity.getChunkCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    String serializeNullableList(List<String> values) {
        if (values == null) {
            return null;
        }
        try {
            return values.isEmpty() ? null : objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("DLP list cannot be serialized", e);
        }
    }

    List<String> deserializeStringList(String values) {
        List<String> list = nullableStringList(values);
        return list == null ? List.of() : list;
    }

    private List<String> nullableStringList(String values) {
        if (values == null || values.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(values, STRING_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("DLP stored list is invalid: " + e.getMessage(), e);
        }
    }

    private List<DlpContentKind> parseContentKinds(String stored) {
        return deserializeStringList(stored).stream()
                .map(value -> DlpConfigValidation.parseEnum(DlpContentKind.class, value, "DLP content kind 无效"))
                .toList();
    }
}
