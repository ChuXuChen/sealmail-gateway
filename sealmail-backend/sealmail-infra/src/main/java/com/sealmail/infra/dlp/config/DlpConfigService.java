package com.sealmail.infra.dlp.config;

import com.sealmail.domain.dlp.DlpContentKind;
import com.sealmail.domain.dlp.DlpMaskingStrategy;
import com.sealmail.domain.dlp.DlpPolicy;
import com.sealmail.domain.dlp.DlpPolicyMode;
import com.sealmail.domain.dlp.DlpRule;
import com.sealmail.domain.dlp.DlpRuleGroup;
import com.sealmail.domain.dlp.DlpRuleType;
import com.sealmail.domain.dlp.DlpScopeType;
import com.sealmail.domain.dlp.config.DlpConfigPort;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.policy.event.DlpPatternConfigChanged;
import com.sealmail.domain.policy.event.DlpSelectionConfigChanged;
import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.DlpPatternEntity;
import com.sealmail.infra.persistence.entity.DlpPolicyEntity;
import com.sealmail.infra.persistence.entity.DlpPolicyRuleGroupEntity;
import com.sealmail.infra.persistence.entity.DlpRuleGroupEntity;
import com.sealmail.infra.persistence.entity.DlpRuleGroupItemEntity;
import com.sealmail.infra.persistence.entity.DlpSelectionEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
@Transactional
public class DlpConfigService implements DlpConfigPort {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final EntityManager entityManager;
    private final DomainEventPublisher domainEventPublisher;
    private final ObjectMapper objectMapper;

    public DlpConfigService(EntityManager entityManager,
                            DomainEventPublisher domainEventPublisher,
                            ObjectMapper objectMapper) {
        this.entityManager = entityManager;
        this.domainEventPublisher = domainEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<DlpPatternConfig> listPatterns() {
        return entityManager.createQuery(
                        "SELECT p FROM DlpPatternEntity p ORDER BY p.priority ASC, p.name ASC",
                        DlpPatternEntity.class)
                .getResultList()
                .stream()
                .map(this::toPatternConfig)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpPatternSettings> listPatternSettings() {
        return listPatterns().stream()
                .map(this::toPatternSettings)
                .toList();
    }

    @Override
    public DlpPatternSettings createPattern(DlpPatternSettingsUpdate update) {
        return toPatternSettings(createPattern(toPatternUpdate(update)));
    }

    @Override
    public DlpPatternSettings updatePattern(String id, DlpPatternSettingsUpdate update) {
        return toPatternSettings(updatePattern(id, toPatternUpdate(update)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpRuleSettings> listRuleSettings() {
        return listRules().stream()
                .map(this::toRuleSettings)
                .toList();
    }

    @Override
    public DlpRuleSettings createRule(DlpRuleSettingsUpdate update) {
        return toRuleSettings(createPattern(toPatternUpdate(update)));
    }

    @Override
    public DlpRuleSettings updateRule(String id, DlpRuleSettingsUpdate update) {
        return toRuleSettings(updatePattern(id, toPatternUpdate(update)));
    }

    @Override
    public void deleteRule(String id) {
        deletePattern(id);
    }

    @Transactional(readOnly = true)
    public List<DlpRule> listRules() {
        return listPatterns().stream()
                .map(this::toRule)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DlpRule> activeRules() {
        return activePatterns().stream()
                .map(this::toRule)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DlpPatternConfig> activePatterns() {
        return entityManager.createQuery(
                        "SELECT p FROM DlpPatternEntity p WHERE p.enabled = true ORDER BY p.priority ASC, p.name ASC",
                        DlpPatternEntity.class)
                .getResultList()
                .stream()
                .map(this::toPatternConfig)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DlpPatternConfig> activePatternsFor(MailEnvelope envelope) {
        List<DlpPatternConfig> activePatterns = activePatterns();
        if (enabledSelections().isEmpty()) {
            return activePatterns;
        }

        List<DlpSelectionConfig> matchingSelections = matchingSelections(envelope);
        if (matchingSelections.isEmpty()) {
            return List.of();
        }

        boolean selectsAllPatterns = matchingSelections.stream()
                .anyMatch(selection -> selection.patternIds() == null);
        if (selectsAllPatterns) {
            return activePatterns;
        }

        Set<String> selectedPatternIds = matchingSelections.stream()
                .filter(selection -> selection.patternIds() != null)
                .flatMap(selection -> selection.patternIds().stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return activePatterns.stream()
                .filter(pattern -> selectedPatternIds.contains(pattern.id()))
                .toList();
    }

    public DlpPatternConfig createPattern(DlpPatternUpdate update) {
        DlpPatternEntity entity = new DlpPatternEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setCreatedAt(Instant.now());
        applyPatternUpdate(entity, update, true);
        entityManager.persist(entity);
        publishPatternChanged(entity, "CREATE");
        return toPatternConfig(entity);
    }

    public DlpPatternConfig updatePattern(String id, DlpPatternUpdate update) {
        DlpPatternEntity entity = requirePattern(id);
        applyPatternUpdate(entity, update, false);
        entityManager.merge(entity);
        publishPatternChanged(entity, "UPDATE");
        return toPatternConfig(entity);
    }

    public void deletePattern(String id) {
        DlpPatternEntity entity = requirePattern(id);
        publishPatternChanged(entity, "DELETE");
        entityManager.remove(entity);
    }

    @Transactional(readOnly = true)
    public List<DlpSelectionConfig> listSelections() {
        return entityManager.createQuery(
                        "SELECT s FROM DlpSelectionEntity s ORDER BY s.scopeType ASC, s.scopeValue ASC",
                        DlpSelectionEntity.class)
                .getResultList()
                .stream()
                .map(this::toSelectionConfig)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpSelectionSettings> listSelectionSettings() {
        return listSelections().stream()
                .map(this::toSelectionSettings)
                .toList();
    }

    @Override
    public DlpSelectionSettings createSelection(DlpSelectionSettingsUpdate update) {
        return toSelectionSettings(createSelection(toSelectionUpdate(update)));
    }

    @Override
    public DlpSelectionSettings updateSelection(String id, DlpSelectionSettingsUpdate update) {
        return toSelectionSettings(updateSelection(id, toSelectionUpdate(update)));
    }

    public DlpSelectionConfig createSelection(DlpSelectionUpdate update) {
        DlpSelectionEntity entity = new DlpSelectionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setCreatedAt(Instant.now());
        applySelectionUpdate(entity, update, true);
        entityManager.persist(entity);
        publishSelectionChanged(entity, "CREATE");
        return toSelectionConfig(entity);
    }

    public DlpSelectionConfig updateSelection(String id, DlpSelectionUpdate update) {
        DlpSelectionEntity entity = requireSelection(id);
        applySelectionUpdate(entity, update, false);
        entityManager.merge(entity);
        publishSelectionChanged(entity, "UPDATE");
        return toSelectionConfig(entity);
    }

    public void deleteSelection(String id) {
        DlpSelectionEntity entity = requireSelection(id);
        publishSelectionChanged(entity, "DELETE");
        entityManager.remove(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpRuleGroupSettings> listRuleGroupSettings() {
        return listRuleGroups().stream()
                .map(group -> new DlpRuleGroupSettings(
                        group.id(),
                        group.name(),
                        group.description(),
                        group.enabled(),
                        group.priority(),
                        group.ruleIds(),
                        group.createdAt(),
                        group.updatedAt()))
                .toList();
    }

    @Override
    public DlpRuleGroupSettings createRuleGroup(DlpRuleGroupSettingsUpdate update) {
        return toRuleGroupSettings(createRuleGroupInternal(update));
    }

    @Override
    public DlpRuleGroupSettings updateRuleGroup(String id, DlpRuleGroupSettingsUpdate update) {
        return toRuleGroupSettings(updateRuleGroupInternal(id, update));
    }

    @Override
    public void deleteRuleGroup(String id) {
        DlpRuleGroupEntity entity = requireRuleGroup(id);
        removeRuleGroupItems(id);
        entityManager.remove(entity);
    }

    @Transactional(readOnly = true)
    public List<DlpRuleGroup> listRuleGroups() {
        return entityManager.createQuery(
                        "SELECT g FROM DlpRuleGroupEntity g ORDER BY g.priority ASC, g.name ASC",
                        DlpRuleGroupEntity.class)
                .getResultList()
                .stream()
                .map(this::toRuleGroup)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DlpRuleGroup> activeRuleGroups() {
        return listRuleGroups().stream()
                .filter(DlpRuleGroup::enabled)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpPolicySettings> listPolicySettings() {
        return listPolicies().stream()
                .map(this::toPolicySettings)
                .toList();
    }

    @Override
    public DlpPolicySettings createPolicy(DlpPolicySettingsUpdate update) {
        return toPolicySettings(createPolicyInternal(update));
    }

    @Override
    public DlpPolicySettings updatePolicy(String id, DlpPolicySettingsUpdate update) {
        return toPolicySettings(updatePolicyInternal(id, update));
    }

    @Override
    public void deletePolicy(String id) {
        DlpPolicyEntity entity = requirePolicy(id);
        removePolicyRuleGroups(id);
        entityManager.remove(entity);
    }

    @Transactional(readOnly = true)
    public List<DlpPolicy> listPolicies() {
        return entityManager.createQuery(
                        "SELECT p FROM DlpPolicyEntity p ORDER BY p.priority ASC, p.name ASC",
                        DlpPolicyEntity.class)
                .getResultList()
                .stream()
                .map(this::toPolicy)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DlpPolicy> activePolicies() {
        return listPolicies().stream()
                .filter(DlpPolicy::enabled)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean appliesTo(MailEnvelope envelope) {
        return !activePatternsFor(envelope).isEmpty();
    }

    @Transactional(readOnly = true)
    public List<DlpSelectionConfig> matchingSelections(MailEnvelope envelope) {
        List<DlpSelectionConfig> selections = enabledSelections();
        if (selections.isEmpty()) {
            return List.of();
        }
        if (envelope == null) {
            return selections.stream()
                    .filter(selection -> selection.scopeType() == DlpScopeType.GLOBAL)
                    .toList();
        }
        String senderDomain = envelope.getSender().getDomain();
        List<String> recipientDomains = envelope.getRecipients().stream()
                .map(EmailAddress::getDomain)
                .toList();

        return selections.stream()
                .sorted(Comparator.comparing(selection -> selection.scopeType().ordinal()))
                .filter(selection -> matchesSelection(selection, senderDomain, recipientDomains))
                .toList();
    }

    private List<DlpSelectionConfig> enabledSelections() {
        return listSelections().stream()
                .filter(DlpSelectionConfig::enabled)
                .toList();
    }

    private boolean matchesSelection(DlpSelectionConfig selection,
                                     String senderDomain,
                                     List<String> recipientDomains) {
        return switch (selection.scopeType()) {
            case GLOBAL -> true;
            case SENDER_DOMAIN -> equalsDomain(selection.scopeValue(), senderDomain);
            case RECIPIENT_DOMAIN -> recipientDomains.stream()
                    .anyMatch(domain -> equalsDomain(selection.scopeValue(), domain));
        };
    }

    private boolean equalsDomain(String expected, String actual) {
        return normalizeDomain(expected).equals(normalizeDomain(actual));
    }

    private DlpRuleGroup createRuleGroupInternal(DlpRuleGroupSettingsUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("DLP rule group request cannot be empty");
        }
        DlpRuleGroupEntity entity = new DlpRuleGroupEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setCreatedAt(Instant.now());
        applyRuleGroupUpdate(entity, update, true);
        entityManager.persist(entity);
        replaceRuleGroupItems(entity.getId(), normalizeIds(update.ruleIds()));
        return toRuleGroup(entity);
    }

    private DlpRuleGroup updateRuleGroupInternal(String id, DlpRuleGroupSettingsUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("DLP rule group request cannot be empty");
        }
        DlpRuleGroupEntity entity = requireRuleGroup(id);
        applyRuleGroupUpdate(entity, update, false);
        entityManager.merge(entity);
        if (update.ruleIds() != null) {
            replaceRuleGroupItems(entity.getId(), normalizeIds(update.ruleIds()));
        }
        return toRuleGroup(entity);
    }

    private DlpPolicy createPolicyInternal(DlpPolicySettingsUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("DLP policy request cannot be empty");
        }
        DlpPolicyEntity entity = new DlpPolicyEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setCreatedAt(Instant.now());
        applyPolicyUpdate(entity, update, true);
        entityManager.persist(entity);
        replacePolicyRuleGroups(entity.getId(), normalizeIds(update.ruleGroupIds()));
        return toPolicy(entity);
    }

    private DlpPolicy updatePolicyInternal(String id, DlpPolicySettingsUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("DLP policy request cannot be empty");
        }
        DlpPolicyEntity entity = requirePolicy(id);
        applyPolicyUpdate(entity, update, false);
        entityManager.merge(entity);
        if (update.ruleGroupIds() != null) {
            replacePolicyRuleGroups(entity.getId(), normalizeIds(update.ruleGroupIds()));
        }
        return toPolicy(entity);
    }

    private void applyPatternUpdate(DlpPatternEntity entity, DlpPatternUpdate update, boolean create) {
        if (update == null) {
            throw new IllegalArgumentException("DLP pattern request cannot be empty");
        }
        DlpRuleType type = create || update.type() != null
                ? parseEnumOrDefault(DlpRuleType.class, update.type(), DlpRuleType.REGEX, "DLP rule type 无效")
                : parseEnumOrDefault(DlpRuleType.class, entity.getRuleType(), DlpRuleType.REGEX, "DLP rule type 无效");
        if (create || update.name() != null) {
            String name = requireText(update.name(), "规则名称不能为空");
            entity.setName(name);
        }
        if (update.description() != null) {
            entity.setDescription(blankToNull(update.description()));
        } else if (create) {
            entity.setDescription(null);
        }
        if (create || update.type() != null) {
            entity.setRuleType(type.name());
        }
        if (update.builtinCode() != null) {
            entity.setBuiltinCode(blankToNull(update.builtinCode()));
        } else if (create) {
            entity.setBuiltinCode(null);
        }
        if (create || update.regex() != null || update.type() != null) {
            String pattern = firstNonNull(update.regex(), entity.getRegex());
            if (type == DlpRuleType.REGEX) {
                pattern = requireText(pattern, "正则表达式不能为空");
                validateRegex(pattern);
            } else if (type == DlpRuleType.KEYWORD) {
                pattern = requireText(pattern, "关键词不能为空");
            } else if (type == DlpRuleType.BUILTIN) {
                if (update.builtinCode() != null || create) {
                    entity.setBuiltinCode(requireText(firstNonNull(update.builtinCode(), entity.getBuiltinCode()), "内置规则编码不能为空"));
                }
            }
            entity.setRegex(blankToNull(pattern));
        }
        if (create || update.contentKinds() != null) {
            entity.setContentKinds(serializeList(parseEnumNames(DlpContentKind.class, update.contentKinds(), "DLP content kind 无效")));
        }
        if (create || update.minMatchCount() != null) {
            int minMatchCount = update.minMatchCount() != null ? update.minMatchCount() : 1;
            require(minMatchCount >= 1, "最小命中数不能小于 1");
            entity.setMinMatchCount(minMatchCount);
        }
        if (create || update.maxEvidenceCount() != null) {
            int maxEvidenceCount = update.maxEvidenceCount() != null ? update.maxEvidenceCount() : 5;
            require(maxEvidenceCount >= 1 && maxEvidenceCount <= 100, "证据上限必须在 1 到 100 之间");
            entity.setMaxEvidenceCount(maxEvidenceCount);
        }
        if (create || update.maskingStrategy() != null) {
            entity.setMaskingStrategy(parseEnumOrDefault(
                    DlpMaskingStrategy.class,
                    update.maskingStrategy(),
                    DlpMaskingStrategy.DEFAULT,
                    "DLP masking strategy 无效").name());
        }
        if (create || update.action() != null) {
            entity.setAction(parseEnum(DispositionAction.class, update.action(), "DLP action 无效").name());
        }
        if (create || update.severity() != null) {
            int severity = update.severity() != null ? update.severity() : 5;
            require(severity >= 1 && severity <= 10, "严重级别必须在 1 到 10 之间");
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

    private void applyRuleGroupUpdate(DlpRuleGroupEntity entity,
                                      DlpRuleGroupSettingsUpdate update,
                                      boolean create) {
        if (create || update.name() != null) {
            entity.setName(requireText(update.name(), "规则组名称不能为空"));
        }
        if (update.description() != null) {
            entity.setDescription(blankToNull(update.description()));
        } else if (create) {
            entity.setDescription(null);
        }
        if (create || update.enabled() != null) {
            entity.setEnabled(update.enabled() == null || update.enabled());
        }
        if (create || update.priority() != null) {
            entity.setPriority(update.priority() != null ? update.priority() : 100);
        }
        entity.setUpdatedAt(Instant.now());
    }

    private void applyPolicyUpdate(DlpPolicyEntity entity,
                                   DlpPolicySettingsUpdate update,
                                   boolean create) {
        if (create || update.name() != null) {
            entity.setName(requireText(update.name(), "策略名称不能为空"));
        }
        if (update.description() != null) {
            entity.setDescription(blankToNull(update.description()));
        } else if (create) {
            entity.setDescription(null);
        }
        if (create || update.mode() != null) {
            entity.setMode(parseEnumOrDefault(DlpPolicyMode.class, update.mode(), DlpPolicyMode.ENFORCE, "DLP policy mode 无效").name());
        }
        if (update.direction() != null) {
            entity.setDirection(blankToNull(update.direction()) == null
                    ? null
                    : parseEnum(MailDirection.class, update.direction(), "DLP policy direction 无效").name());
        } else if (create) {
            entity.setDirection(null);
        }
        if (create || update.senderDomains() != null) {
            entity.setSenderDomains(serializeList(normalizeDomains(update.senderDomains())));
        }
        if (create || update.recipientDomains() != null) {
            entity.setRecipientDomains(serializeList(normalizeDomains(update.recipientDomains())));
        }
        if (create || update.senderAddressPatterns() != null) {
            validateWildcards(update.senderAddressPatterns());
            entity.setSenderAddressPatterns(serializeList(normalizeTextList(update.senderAddressPatterns())));
        }
        if (create || update.recipientAddressPatterns() != null) {
            validateWildcards(update.recipientAddressPatterns());
            entity.setRecipientAddressPatterns(serializeList(normalizeTextList(update.recipientAddressPatterns())));
        }
        if (create || update.attachmentRequired() != null) {
            entity.setAttachmentRequired(update.attachmentRequired() != null && update.attachmentRequired());
        }
        if (create || update.enabled() != null) {
            entity.setEnabled(update.enabled() == null || update.enabled());
        }
        if (create || update.priority() != null) {
            entity.setPriority(update.priority() != null ? update.priority() : 100);
        }
        entity.setUpdatedAt(Instant.now());
    }

    private void applySelectionUpdate(DlpSelectionEntity entity, DlpSelectionUpdate update, boolean create) {
        if (update == null) {
            throw new IllegalArgumentException("DLP selection request cannot be empty");
        }
        DlpScopeType scopeType = create || update.scopeType() != null
                ? parseEnum(DlpScopeType.class, update.scopeType(), "DLP 生效范围无效")
                : DlpScopeType.valueOf(entity.getScopeType());
        entity.setScopeType(scopeType.name());
        if (scopeType == DlpScopeType.GLOBAL) {
            entity.setScopeValue(null);
        } else if (create || update.scopeValue() != null) {
            entity.setScopeValue(requireText(normalizeDomain(update.scopeValue()), "域名不能为空"));
        }
        if (create || update.patternIds() != null || update.patternMode() != null) {
            if (selectsAllPatterns(update)) {
                entity.setPatternIds(null);
            } else {
                List<String> patternIds = normalizePatternIds(update.patternIds());
                require(patternIds != null && !patternIds.isEmpty(), "请选择至少一条 DLP 规则，或切换为全部规则");
                patternIds.forEach(this::requirePattern);
                entity.setPatternIds(serializePatternIds(patternIds));
            }
        }
        if (create || update.enabled() != null) {
            entity.setEnabled(update.enabled() == null || update.enabled());
        }
        entity.setUpdatedAt(Instant.now());
    }

    private DlpPatternEntity requirePattern(String id) {
        DlpPatternEntity entity = entityManager.find(DlpPatternEntity.class, id);
        if (entity == null) {
            throw new IllegalArgumentException("DLP pattern not found: " + id);
        }
        return entity;
    }

    private DlpSelectionEntity requireSelection(String id) {
        DlpSelectionEntity entity = entityManager.find(DlpSelectionEntity.class, id);
        if (entity == null) {
            throw new IllegalArgumentException("DLP selection not found: " + id);
        }
        return entity;
    }

    private DlpRuleGroupEntity requireRuleGroup(String id) {
        DlpRuleGroupEntity entity = entityManager.find(DlpRuleGroupEntity.class, id);
        if (entity == null) {
            throw new IllegalArgumentException("DLP rule group not found: " + id);
        }
        return entity;
    }

    private DlpPolicyEntity requirePolicy(String id) {
        DlpPolicyEntity entity = entityManager.find(DlpPolicyEntity.class, id);
        if (entity == null) {
            throw new IllegalArgumentException("DLP policy not found: " + id);
        }
        return entity;
    }

    private DlpPatternConfig toPatternConfig(DlpPatternEntity entity) {
        return new DlpPatternConfig(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getRegex(),
                parseEnumOrDefault(DlpRuleType.class, entity.getRuleType(), DlpRuleType.REGEX, "DLP rule type 无效"),
                entity.getBuiltinCode(),
                parseContentKinds(entity.getContentKinds()),
                entity.getMinMatchCount() > 0 ? entity.getMinMatchCount() : 1,
                entity.getMaxEvidenceCount() > 0 ? entity.getMaxEvidenceCount() : 5,
                parseEnumOrDefault(DlpMaskingStrategy.class, entity.getMaskingStrategy(), DlpMaskingStrategy.DEFAULT, "DLP masking strategy 无效"),
                DispositionAction.valueOf(entity.getAction()),
                entity.getSeverity(),
                entity.getPriority(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private DlpPatternSettings toPatternSettings(DlpPatternConfig config) {
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

    private DlpPatternUpdate toPatternUpdate(DlpPatternSettingsUpdate update) {
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

    private DlpPatternUpdate toPatternUpdate(DlpRuleSettingsUpdate update) {
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

    private DlpRule toRule(DlpPatternConfig config) {
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

    private DlpRuleSettings toRuleSettings(DlpPatternConfig config) {
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

    private DlpRuleSettings toRuleSettings(DlpRule rule) {
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

    private DlpSelectionConfig toSelectionConfig(DlpSelectionEntity entity) {
        return new DlpSelectionConfig(
                entity.getId(),
                DlpScopeType.valueOf(entity.getScopeType()),
                entity.getScopeValue(),
                deserializePatternIds(entity.getPatternIds()),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private DlpSelectionSettings toSelectionSettings(DlpSelectionConfig config) {
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

    private DlpSelectionUpdate toSelectionUpdate(DlpSelectionSettingsUpdate update) {
        return new DlpSelectionUpdate(
                update.scopeType(),
                update.scopeValue(),
                update.patternIds(),
                update.patternMode(),
                update.enabled()
        );
    }

    private DlpRuleGroup toRuleGroup(DlpRuleGroupEntity entity) {
        return new DlpRuleGroup(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.isEnabled(),
                entity.getPriority(),
                ruleIdsForGroup(entity.getId()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private DlpRuleGroupSettings toRuleGroupSettings(DlpRuleGroup group) {
        return new DlpRuleGroupSettings(
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

    private DlpPolicy toPolicy(DlpPolicyEntity entity) {
        return new DlpPolicy(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                parseEnumOrDefault(DlpPolicyMode.class, entity.getMode(), DlpPolicyMode.ENFORCE, "DLP policy mode 无效"),
                parseNullableEnum(MailDirection.class, entity.getDirection()),
                deserializeStringList(entity.getSenderDomains()),
                deserializeStringList(entity.getRecipientDomains()),
                deserializeStringList(entity.getSenderAddressPatterns()),
                deserializeStringList(entity.getRecipientAddressPatterns()),
                entity.isAttachmentRequired(),
                entity.isEnabled(),
                entity.getPriority(),
                ruleGroupIdsForPolicy(entity.getId()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private DlpPolicySettings toPolicySettings(DlpPolicy policy) {
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

    private List<String> ruleIdsForGroup(String groupId) {
        return entityManager.createQuery(
                        "SELECT i FROM DlpRuleGroupItemEntity i WHERE i.ruleGroupId = :groupId ORDER BY i.position ASC, i.createdAt ASC",
                        DlpRuleGroupItemEntity.class)
                .setParameter("groupId", groupId)
                .getResultList()
                .stream()
                .map(DlpRuleGroupItemEntity::getRuleId)
                .toList();
    }

    private List<String> ruleGroupIdsForPolicy(String policyId) {
        return entityManager.createQuery(
                        "SELECT i FROM DlpPolicyRuleGroupEntity i WHERE i.policyId = :policyId ORDER BY i.position ASC, i.createdAt ASC",
                        DlpPolicyRuleGroupEntity.class)
                .setParameter("policyId", policyId)
                .getResultList()
                .stream()
                .map(DlpPolicyRuleGroupEntity::getRuleGroupId)
                .toList();
    }

    private void replaceRuleGroupItems(String groupId, List<String> ruleIds) {
        removeRuleGroupItems(groupId);
        List<String> normalized = ruleIds == null ? List.of() : ruleIds;
        for (String ruleId : normalized) {
            requirePattern(ruleId);
        }
        int position = 0;
        for (String ruleId : normalized) {
            DlpRuleGroupItemEntity item = new DlpRuleGroupItemEntity();
            item.setId(UUID.randomUUID().toString());
            item.setRuleGroupId(groupId);
            item.setRuleId(ruleId);
            item.setPosition(position++);
            item.setCreatedAt(Instant.now());
            entityManager.persist(item);
        }
    }

    private void replacePolicyRuleGroups(String policyId, List<String> ruleGroupIds) {
        removePolicyRuleGroups(policyId);
        List<String> normalized = ruleGroupIds == null ? List.of() : ruleGroupIds;
        for (String ruleGroupId : normalized) {
            requireRuleGroup(ruleGroupId);
        }
        int position = 0;
        for (String ruleGroupId : normalized) {
            DlpPolicyRuleGroupEntity item = new DlpPolicyRuleGroupEntity();
            item.setId(UUID.randomUUID().toString());
            item.setPolicyId(policyId);
            item.setRuleGroupId(ruleGroupId);
            item.setPosition(position++);
            item.setCreatedAt(Instant.now());
            entityManager.persist(item);
        }
    }

    private void removeRuleGroupItems(String groupId) {
        entityManager.createQuery("DELETE FROM DlpRuleGroupItemEntity i WHERE i.ruleGroupId = :groupId")
                .setParameter("groupId", groupId)
                .executeUpdate();
    }

    private void removePolicyRuleGroups(String policyId) {
        entityManager.createQuery("DELETE FROM DlpPolicyRuleGroupEntity i WHERE i.policyId = :policyId")
                .setParameter("policyId", policyId)
                .executeUpdate();
    }

    private void validateRegex(String regex) {
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("正则表达式无效: " + e.getMessage());
        }
    }

    private void validateWildcards(List<String> patterns) {
        for (String pattern : normalizeTextList(patterns)) {
            wildcardToRegex(pattern);
        }
    }

    private void publishPatternChanged(DlpPatternEntity entity, String operation) {
        domainEventPublisher.publishEvent(new DlpPatternConfigChanged(
                entity.getId(),
                operation,
                entity.getName()));
    }

    private void publishSelectionChanged(DlpSelectionEntity entity, String operation) {
        domainEventPublisher.publishEvent(new DlpSelectionConfigChanged(
                entity.getId(),
                operation,
                DlpScopeType.valueOf(entity.getScopeType()),
                entity.getScopeValue()));
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }

    private static String normalizeDomain(String value) {
        return Optional.ofNullable(value)
                .orElse("")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\.+$", "");
    }

    private static List<String> normalizeDomains(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(DlpConfigService::normalizeDomain)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static List<String> normalizeTextList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static List<String> normalizeIds(List<String> values) {
        return normalizeTextList(values);
    }

    private static List<String> normalizePatternIds(List<String> patternIds) {
        if (patternIds == null) {
            return null;
        }
        return patternIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static boolean selectsAllPatterns(DlpSelectionUpdate update) {
        if (update.patternMode() == null || update.patternMode().isBlank()) {
            return update.patternIds() == null;
        }
        return "ALL".equalsIgnoreCase(update.patternMode());
    }

    private String serializePatternIds(List<String> patternIds) {
        return serializeList(patternIds);
    }

    private String serializeList(List<String> values) {
        if (values == null) {
            return null;
        }
        try {
            List<String> normalized = values == null ? List.of() : values;
            return normalized.isEmpty() ? null : objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("DLP list cannot be serialized", e);
        }
    }

    private List<String> deserializePatternIds(String patternIds) {
        return deserializeNullableStringList(patternIds);
    }

    private List<String> deserializeStringList(String values) {
        List<String> list = deserializeNullableStringList(values);
        return list == null ? List.of() : list;
    }

    private List<String> deserializeNullableStringList(String values) {
        if (values == null || values.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(values, STRING_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("DLP stored list is invalid: " + e.getMessage(), e);
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String message) {
        try {
            return Enum.valueOf(enumClass, requireText(value, message).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(message);
        }
    }

    private static <E extends Enum<E>> E parseEnumOrDefault(Class<E> enumClass, String value, E defaultValue, String message) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return parseEnum(enumClass, value, message);
    }

    private static <E extends Enum<E>> E parseNullableEnum(Class<E> enumClass, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Enum.valueOf(enumClass, value.toUpperCase(Locale.ROOT));
    }

    private static <E extends Enum<E>> List<String> parseEnumNames(Class<E> enumClass, List<String> values, String message) {
        return normalizeTextList(values).stream()
                .map(value -> parseEnum(enumClass, value, message).name())
                .toList();
    }

    private List<DlpContentKind> parseContentKinds(String stored) {
        return deserializeStringList(stored).stream()
                .map(value -> parseEnum(DlpContentKind.class, value, "DLP content kind 无效"))
                .toList();
    }

    private static String wildcardToRegex(String pattern) {
        StringBuilder sb = new StringBuilder();
        for (char ch : pattern.toCharArray()) {
            if (ch == '*') {
                sb.append(".*");
            } else if (".[]{}()+-^$?|\\ ".indexOf(ch) >= 0) {
                sb.append('\\').append(ch);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
