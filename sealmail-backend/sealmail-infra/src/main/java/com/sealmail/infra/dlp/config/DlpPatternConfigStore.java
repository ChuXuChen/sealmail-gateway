package com.sealmail.infra.dlp.config;

import com.sealmail.domain.dlp.DlpContentKind;
import com.sealmail.domain.dlp.DlpMaskingStrategy;
import com.sealmail.domain.dlp.DlpRule;
import com.sealmail.domain.dlp.DlpRuleType;
import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.infra.persistence.entity.DlpPatternEntity;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
class DlpPatternConfigStore implements DlpPatternConfigPort {

    private final EntityManager entityManager;
    private final DlpConfigMapper mapper;
    private final DlpConfigEvents events;

    DlpPatternConfigStore(EntityManager entityManager,
                          DlpConfigMapper mapper,
                          DlpConfigEvents events) {
        this.entityManager = entityManager;
        this.mapper = mapper;
        this.events = events;
    }

    @Override
    public List<DlpPatternConfig> listPatterns() {
        return entityManager.createQuery(
                        "SELECT p FROM DlpPatternEntity p ORDER BY p.priority ASC, p.name ASC",
                        DlpPatternEntity.class)
                .getResultList()
                .stream()
                .map(mapper::toPatternConfig)
                .toList();
    }

    @Override
    public List<DlpRule> listRules() {
        return listPatterns().stream()
                .map(mapper::toRule)
                .toList();
    }

    @Override
    public List<DlpRule> activeRules() {
        return activePatterns().stream()
                .map(mapper::toRule)
                .toList();
    }

    @Override
    public List<DlpPatternConfig> activePatterns() {
        return entityManager.createQuery(
                        "SELECT p FROM DlpPatternEntity p WHERE p.enabled = true ORDER BY p.priority ASC, p.name ASC",
                        DlpPatternEntity.class)
                .getResultList()
                .stream()
                .map(mapper::toPatternConfig)
                .toList();
    }

    @Override
    public DlpPatternConfig createPattern(DlpPatternUpdate update) {
        DlpPatternEntity entity = new DlpPatternEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setCreatedAt(Instant.now());
        applyPatternUpdate(entity, update, true);
        entityManager.persist(entity);
        events.publishPatternChanged(entity, "CREATE");
        return mapper.toPatternConfig(entity);
    }

    @Override
    public DlpPatternConfig updatePattern(String id, DlpPatternUpdate update) {
        DlpPatternEntity entity = requirePattern(id);
        applyPatternUpdate(entity, update, false);
        entityManager.merge(entity);
        events.publishPatternChanged(entity, "UPDATE");
        return mapper.toPatternConfig(entity);
    }

    @Override
    public void deletePattern(String id) {
        DlpPatternEntity entity = requirePattern(id);
        events.publishPatternChanged(entity, "DELETE");
        entityManager.remove(entity);
    }

    DlpPatternEntity requirePattern(String id) {
        DlpPatternEntity entity = entityManager.find(DlpPatternEntity.class, id);
        if (entity == null) {
            throw new IllegalArgumentException("DLP pattern not found: " + id);
        }
        return entity;
    }

    private void applyPatternUpdate(DlpPatternEntity entity, DlpPatternUpdate update, boolean create) {
        if (update == null) {
            throw new IllegalArgumentException("DLP pattern request cannot be empty");
        }
        DlpRuleType requestedType = create || update.type() != null
                ? DlpConfigValidation.parseEnumOrDefault(DlpRuleType.class, update.type(), DlpRuleType.PATTERN, "DLP rule type 无效")
                : DlpConfigValidation.parseEnumOrDefault(DlpRuleType.class, entity.getRuleType(), DlpRuleType.PATTERN, "DLP rule type 无效");
        if (create || update.name() != null) {
            entity.setName(DlpConfigValidation.requireText(update.name(), "规则名称不能为空"));
        }
        if (update.description() != null) {
            entity.setDescription(DlpConfigValidation.blankToNull(update.description()));
        } else if (create) {
            entity.setDescription(null);
        }
        if (update.builtinCode() != null) {
            entity.setBuiltinCode(DlpConfigValidation.blankToNull(update.builtinCode()));
        } else if (create) {
            entity.setBuiltinCode(null);
        }
        if (create || update.regex() != null || update.type() != null) {
            String pattern = DlpConfigValidation.firstNonNull(update.regex(), entity.getRegex());
            String builtinCode = DlpConfigValidation.firstNonNull(update.builtinCode(), entity.getBuiltinCode());
            if (requestedType == DlpRuleType.REGEX || requestedType == DlpRuleType.PATTERN) {
                pattern = DlpConfigValidation.requireText(pattern, "检测模式不能为空");
                DlpConfigValidation.validateRegex(pattern);
                entity.setRuleType(DlpRuleType.PATTERN.name());
            } else if (requestedType == DlpRuleType.KEYWORD) {
                pattern = DlpConfigValidation.requireText(pattern, "关键词不能为空");
                pattern = DlpConfigValidation.keywordRegex(pattern);
                DlpConfigValidation.validateRegex(pattern);
                entity.setRuleType(DlpRuleType.PATTERN.name());
            } else if (requestedType == DlpRuleType.BUILTIN) {
                String code = DlpConfigValidation.requireText(builtinCode, "内置规则编码不能为空");
                entity.setBuiltinCode(code);
                pattern = DlpConfigValidation.builtinRegex(code);
                entity.setRuleType(DlpRuleType.PATTERN.name());
            } else if (requestedType == DlpRuleType.EDM) {
                pattern = DlpConfigValidation.requireText(pattern, "EDM 数据集 ID 不能为空");
                entity.setRuleType(DlpRuleType.EDM.name());
            } else if (requestedType == DlpRuleType.FINGERPRINT) {
                pattern = DlpConfigValidation.requireText(pattern, "文档指纹库 ID 不能为空");
                entity.setRuleType(DlpRuleType.FINGERPRINT.name());
            } else {
                throw new IllegalArgumentException("DLP rule type 无效");
            }
            entity.setRegex(DlpConfigValidation.blankToNull(pattern));
        } else if (create) {
            entity.setRuleType(DlpRuleType.PATTERN.name());
        } else if (update.type() != null) {
            entity.setRuleType(DlpConfigValidation.canonicalType(requestedType).name());
        }
        if (create || update.contentKinds() != null) {
            entity.setContentKinds(mapper.serializeNullableList(DlpConfigValidation.parseEnumNames(
                    DlpContentKind.class,
                    update.contentKinds(),
                    "DLP content kind 无效")));
        }
        if (create || update.minMatchCount() != null) {
            int minMatchCount = update.minMatchCount() != null ? update.minMatchCount() : 1;
            DlpConfigValidation.require(minMatchCount >= 1, "最小命中数不能小于 1");
            entity.setMinMatchCount(minMatchCount);
        }
        if (create || update.maxEvidenceCount() != null) {
            int maxEvidenceCount = update.maxEvidenceCount() != null ? update.maxEvidenceCount() : 5;
            DlpConfigValidation.require(maxEvidenceCount >= 1 && maxEvidenceCount <= 100, "证据上限必须在 1 到 100 之间");
            entity.setMaxEvidenceCount(maxEvidenceCount);
        }
        if (create || update.maskingStrategy() != null) {
            DlpMaskingStrategy defaultMasking = switch (DlpConfigValidation.canonicalType(requestedType)) {
                case EDM, FINGERPRINT -> DlpMaskingStrategy.HASH_ONLY;
                default -> DlpMaskingStrategy.DEFAULT;
            };
            entity.setMaskingStrategy(DlpConfigValidation.parseEnumOrDefault(
                    DlpMaskingStrategy.class,
                    update.maskingStrategy(),
                    defaultMasking,
                    "DLP masking strategy 无效").name());
        }
        if (create || update.action() != null) {
            entity.setAction(DlpConfigValidation.parseEnum(DispositionAction.class, update.action(), "DLP action 无效").name());
        }
        if (create || update.severity() != null) {
            int severity = update.severity() != null ? update.severity() : 5;
            DlpConfigValidation.require(severity >= 1 && severity <= 10, "严重级别必须在 1 到 10 之间");
            entity.setSeverity(severity);
        }
        if (create || update.priority() != null) {
            entity.setPriority(update.priority() != null ? update.priority() : 100);
        }
        if (create || update.enabled() != null) {
            entity.setEnabled(update.enabled() == null || update.enabled());
        }
        entity.setUpdatedAt(Instant.now());
    }
}
