package com.sealmail.infra.dlp.config;

import com.sealmail.domain.dlp.DlpScopeType;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.policy.event.DlpPatternConfigChanged;
import com.sealmail.domain.policy.event.DlpSelectionConfigChanged;
import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.DlpPatternEntity;
import com.sealmail.infra.persistence.entity.DlpSelectionEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
public class DlpConfigService {

    @PersistenceContext
    private EntityManager entityManager;

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final DomainEventPublisher domainEventPublisher;
    private final ObjectMapper objectMapper;

    public DlpConfigService(DomainEventPublisher domainEventPublisher, ObjectMapper objectMapper) {
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

    private void applyPatternUpdate(DlpPatternEntity entity, DlpPatternUpdate update, boolean create) {
        if (update == null) {
            throw new IllegalArgumentException("DLP pattern request cannot be empty");
        }
        if (create || update.name() != null) {
            String name = requireText(update.name(), "规则名称不能为空");
            entity.setName(name);
        }
        if (update.description() != null) {
            entity.setDescription(blankToNull(update.description()));
        } else if (create) {
            entity.setDescription(null);
        }
        if (create || update.regex() != null) {
            String regex = requireText(update.regex(), "正则表达式不能为空");
            validateRegex(regex);
            entity.setRegex(regex);
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

    private DlpPatternConfig toPatternConfig(DlpPatternEntity entity) {
        return new DlpPatternConfig(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getRegex(),
                DispositionAction.valueOf(entity.getAction()),
                entity.getSeverity(),
                entity.getPriority(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
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

    private void validateRegex(String regex) {
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("正则表达式无效: " + e.getMessage());
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

    private static String normalizeDomain(String value) {
        return Optional.ofNullable(value)
                .orElse("")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\.+$", "");
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
        if (patternIds == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(patternIds);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("DLP pattern selection cannot be serialized", e);
        }
    }

    private List<String> deserializePatternIds(String patternIds) {
        if (patternIds == null || patternIds.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(patternIds, STRING_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("DLP pattern selection is invalid: " + e.getMessage(), e);
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String message) {
        try {
            return Enum.valueOf(enumClass, requireText(value, message).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(message);
        }
    }
}
