package com.sealmail.infra.dlp.config;

import com.sealmail.domain.dlp.DlpScopeType;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.persistence.entity.DlpSelectionEntity;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
class DlpSelectionConfigStore implements DlpSelectionConfigPort {

    private final EntityManager entityManager;
    private final DlpConfigMapper mapper;
    private final DlpConfigEvents events;
    private final DlpPatternConfigStore patternStore;

    DlpSelectionConfigStore(EntityManager entityManager,
                            DlpConfigMapper mapper,
                            DlpConfigEvents events,
                            DlpPatternConfigStore patternStore) {
        this.entityManager = entityManager;
        this.mapper = mapper;
        this.events = events;
        this.patternStore = patternStore;
    }

    @Override
    public List<DlpSelectionConfig> listSelections() {
        return entityManager.createQuery(
                        "SELECT s FROM DlpSelectionEntity s ORDER BY s.scopeType ASC, s.scopeValue ASC",
                        DlpSelectionEntity.class)
                .getResultList()
                .stream()
                .map(mapper::toSelectionConfig)
                .toList();
    }

    @Override
    public DlpSelectionConfig createSelection(DlpSelectionUpdate update) {
        DlpSelectionEntity entity = new DlpSelectionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setCreatedAt(Instant.now());
        applySelectionUpdate(entity, update, true);
        entityManager.persist(entity);
        events.publishSelectionChanged(entity, "CREATE");
        return mapper.toSelectionConfig(entity);
    }

    @Override
    public DlpSelectionConfig updateSelection(String id, DlpSelectionUpdate update) {
        DlpSelectionEntity entity = requireSelection(id);
        applySelectionUpdate(entity, update, false);
        entityManager.merge(entity);
        events.publishSelectionChanged(entity, "UPDATE");
        return mapper.toSelectionConfig(entity);
    }

    @Override
    public void deleteSelection(String id) {
        DlpSelectionEntity entity = requireSelection(id);
        events.publishSelectionChanged(entity, "DELETE");
        entityManager.remove(entity);
    }

    @Override
    public List<DlpPatternConfig> activePatternsFor(MailEnvelope envelope) {
        List<DlpPatternConfig> activePatterns = patternStore.activePatterns();
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

    @Override
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

    @Override
    public boolean appliesTo(MailEnvelope envelope) {
        return !activePatternsFor(envelope).isEmpty();
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
        return DlpConfigValidation.normalizeDomain(expected).equals(DlpConfigValidation.normalizeDomain(actual));
    }

    private DlpSelectionEntity requireSelection(String id) {
        DlpSelectionEntity entity = entityManager.find(DlpSelectionEntity.class, id);
        if (entity == null) {
            throw new IllegalArgumentException("DLP selection not found: " + id);
        }
        return entity;
    }

    private void applySelectionUpdate(DlpSelectionEntity entity, DlpSelectionUpdate update, boolean create) {
        if (update == null) {
            throw new IllegalArgumentException("DLP selection request cannot be empty");
        }
        DlpScopeType scopeType = create || update.scopeType() != null
                ? DlpConfigValidation.parseEnum(DlpScopeType.class, update.scopeType(), "DLP 生效范围无效")
                : DlpScopeType.valueOf(entity.getScopeType());
        entity.setScopeType(scopeType.name());
        if (scopeType == DlpScopeType.GLOBAL) {
            entity.setScopeValue(null);
        } else if (create || update.scopeValue() != null) {
            entity.setScopeValue(DlpConfigValidation.requireText(
                    DlpConfigValidation.normalizeDomain(update.scopeValue()),
                    "域名不能为空"));
        }
        if (create || update.patternIds() != null || update.patternMode() != null) {
            if (DlpConfigValidation.selectsAllPatterns(update)) {
                entity.setAllPatterns(true);
                entity.getPatternIds().clear();
            } else {
                List<String> patternIds = DlpConfigValidation.nullableNormalizedPatternIds(update.patternIds());
                DlpConfigValidation.require(patternIds != null && !patternIds.isEmpty(), "请选择至少一条 DLP 规则，或切换为全部规则");
                patternIds.forEach(patternStore::requirePattern);
                entity.setAllPatterns(false);
                entity.setPatternIds(new ArrayList<>(patternIds));
            }
        }
        if (create || update.enabled() != null) {
            entity.setEnabled(update.enabled() == null || update.enabled());
        }
        entity.setUpdatedAt(Instant.now());
    }
}
