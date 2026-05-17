package com.sealmail.infra.persistence.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.dlp.DlpContentKind;
import com.sealmail.domain.dlp.DlpEvidence;
import com.sealmail.domain.dlp.DlpRuleType;
import com.sealmail.domain.dlp.DlpScanEvent;
import com.sealmail.domain.dlp.spi.DlpEventRepository;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.infra.persistence.entity.DlpScanEventEntity;
import com.sealmail.infra.persistence.entity.DlpScanEvidenceEntity;
import com.sealmail.infra.persistence.entity.QuarantinedMailEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class DlpEventRepositoryImpl implements DlpEventRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper;

    public DlpEventRepositoryImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public DlpScanEvent save(DlpScanEvent event, List<DlpEvidence> evidence) {
        DlpScanEventEntity entity = toEntity(event);
        entityManager.persist(entity);
        for (DlpEvidence item : evidence == null ? List.<DlpEvidence>of() : evidence) {
            entityManager.persist(toEntity(item));
        }
        return event;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpScanEvent> findEvents(int offset,
                                         int limit,
                                         String action,
                                         Integer minSeverity,
                                         String rule,
                                         String domain) {
        QueryParts parts = queryParts(action, minSeverity, rule, domain, false);
        TypedQuery<DlpScanEventEntity> query = entityManager.createQuery(
                "SELECT DISTINCT e FROM DlpScanEventEntity e " + parts.join()
                        + parts.where()
                        + " ORDER BY e.createdAt DESC",
                DlpScanEventEntity.class);
        bind(query, parts);
        query.setFirstResult(Math.max(0, offset));
        query.setMaxResults(Math.max(1, limit));
        return query.getResultList().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countEvents(String action, Integer minSeverity, String rule, String domain) {
        QueryParts parts = queryParts(action, minSeverity, rule, domain, true);
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(DISTINCT e.id) FROM DlpScanEventEntity e "
                        + parts.join()
                        + parts.where(),
                Long.class);
        bind(query, parts);
        return query.getSingleResult();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DlpScanEvent> findEventById(String id) {
        DlpScanEventEntity entity = entityManager.find(DlpScanEventEntity.class, id);
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpEvidence> findEvidenceByEventId(String eventId) {
        return entityManager.createQuery(
                        "SELECT e FROM DlpScanEvidenceEntity e WHERE e.eventId = :eventId ORDER BY e.severity DESC, e.createdAt ASC",
                        DlpScanEvidenceEntity.class)
                .setParameter("eventId", eventId)
                .getResultList()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpEvidence> findEvidenceByQuarantineId(String quarantineId) {
        Optional<String> eventId = eventIdForQuarantine(quarantineId);
        return eventId.map(this::findEvidenceByEventId).orElse(List.of());
    }

    @Override
    public void linkQuarantine(String eventId, String quarantineId) {
        if (eventId == null || eventId.isBlank() || quarantineId == null || quarantineId.isBlank()) {
            return;
        }
        DlpScanEventEntity event = entityManager.find(DlpScanEventEntity.class, eventId);
        if (event != null) {
            event.setQuarantineId(quarantineId);
            entityManager.merge(event);
        }
        QuarantinedMailEntity quarantine = entityManager.find(QuarantinedMailEntity.class, quarantineId);
        if (quarantine != null) {
            quarantine.setDlpEventId(eventId);
            entityManager.merge(quarantine);
        }
    }

    @Override
    public void markQuarantineFalsePositive(String quarantineId, String operator, String comment) {
        QuarantinedMailEntity quarantine = entityManager.find(QuarantinedMailEntity.class, quarantineId);
        if (quarantine == null) {
            throw new IllegalArgumentException("DLP quarantine not found: " + quarantineId);
        }
        Instant now = Instant.now();
        quarantine.setFalsePositive(true);
        quarantine.setFalsePositiveAt(now);
        quarantine.setFalsePositiveBy(operator);
        quarantine.setFalsePositiveComment(comment);
        entityManager.merge(quarantine);

        Optional<String> eventId = Optional.ofNullable(quarantine.getDlpEventId())
                .filter(value -> !value.isBlank())
                .or(() -> eventIdForQuarantine(quarantineId));
        eventId.map(id -> entityManager.find(DlpScanEventEntity.class, id))
                .ifPresent(event -> {
                    event.setFalsePositive(true);
                    event.setFalsePositiveAt(now);
                    event.setFalsePositiveBy(operator);
                    event.setFalsePositiveComment(comment);
                    entityManager.merge(event);
                });
    }

    private Optional<String> eventIdForQuarantine(String quarantineId) {
        List<String> ids = entityManager.createQuery(
                        "SELECT e.id FROM DlpScanEventEntity e WHERE e.quarantineId = :quarantineId ORDER BY e.createdAt DESC",
                        String.class)
                .setParameter("quarantineId", quarantineId)
                .setMaxResults(1)
                .getResultList();
        if (!ids.isEmpty()) {
            return Optional.of(ids.getFirst());
        }
        QuarantinedMailEntity quarantine = entityManager.find(QuarantinedMailEntity.class, quarantineId);
        return Optional.ofNullable(quarantine)
                .map(QuarantinedMailEntity::getDlpEventId)
                .filter(value -> !value.isBlank());
    }

    private DlpScanEventEntity toEntity(DlpScanEvent event) {
        DlpScanEventEntity entity = new DlpScanEventEntity();
        entity.setId(event.id());
        entity.setMessageId(event.messageId());
        entity.setProcessingId(event.processingId());
        entity.setDirection(event.direction() != null ? event.direction().name() : null);
        entity.setSenderEmail(event.senderEmail());
        entity.setRecipients(serialize(event.recipients()));
        entity.setSubject(event.subject());
        entity.setRemoteAddress(event.remoteAddress());
        entity.setPolicyIds(serialize(event.policyIds()));
        entity.setRuleGroupIds(serialize(event.ruleGroupIds()));
        entity.setAction(event.action().name());
        entity.setMaxSeverity(event.maxSeverity());
        entity.setMatchCount(event.matchCount());
        entity.setExtractionWarnings(serialize(event.extractionWarnings()));
        entity.setMonitorMode(event.monitorMode());
        entity.setScanDurationMs(event.scanDurationMs());
        entity.setQuarantineId(event.quarantineId());
        entity.setFalsePositive(event.falsePositive());
        entity.setFalsePositiveAt(event.falsePositiveAt());
        entity.setFalsePositiveBy(event.falsePositiveBy());
        entity.setFalsePositiveComment(event.falsePositiveComment());
        entity.setCreatedAt(event.createdAt() != null ? event.createdAt() : Instant.now());
        return entity;
    }

    private DlpScanEvent toDomain(DlpScanEventEntity entity) {
        return new DlpScanEvent(
                entity.getId(),
                entity.getMessageId(),
                entity.getProcessingId(),
                parseDirection(entity.getDirection()),
                entity.getSenderEmail(),
                deserialize(entity.getRecipients()),
                entity.getSubject(),
                entity.getRemoteAddress(),
                deserialize(entity.getPolicyIds()),
                deserialize(entity.getRuleGroupIds()),
                DispositionAction.valueOf(entity.getAction()),
                entity.getMaxSeverity(),
                entity.getMatchCount(),
                deserialize(entity.getExtractionWarnings()),
                entity.isMonitorMode(),
                entity.getScanDurationMs(),
                entity.getQuarantineId(),
                entity.isFalsePositive(),
                entity.getFalsePositiveAt(),
                entity.getFalsePositiveBy(),
                entity.getFalsePositiveComment(),
                entity.getCreatedAt()
        );
    }

    private DlpScanEvidenceEntity toEntity(DlpEvidence evidence) {
        DlpScanEvidenceEntity entity = new DlpScanEvidenceEntity();
        entity.setId(evidence.id());
        entity.setEventId(evidence.eventId());
        entity.setRuleId(evidence.ruleId());
        entity.setRuleName(evidence.ruleName());
        entity.setRuleType(evidence.ruleType().name());
        entity.setPartId(evidence.partId());
        entity.setPartKind(evidence.partKind().name());
        entity.setFileName(evidence.fileName());
        entity.setContentType(evidence.contentType());
        entity.setMaskedSnippet(evidence.maskedSnippet());
        entity.setMatchHash(evidence.matchHash());
        entity.setStartOffset(evidence.startOffset());
        entity.setEndOffset(evidence.endOffset());
        entity.setSeverity(evidence.severity());
        entity.setAction(evidence.action().name());
        entity.setCreatedAt(evidence.createdAt() != null ? evidence.createdAt() : Instant.now());
        return entity;
    }

    private DlpEvidence toDomain(DlpScanEvidenceEntity entity) {
        return new DlpEvidence(
                entity.getId(),
                entity.getEventId(),
                entity.getRuleId(),
                entity.getRuleName(),
                DlpRuleType.valueOf(entity.getRuleType()),
                entity.getPartId(),
                DlpContentKind.valueOf(entity.getPartKind()),
                entity.getFileName(),
                entity.getContentType(),
                entity.getMaskedSnippet(),
                entity.getMatchHash(),
                entity.getStartOffset(),
                entity.getEndOffset(),
                entity.getSeverity(),
                DispositionAction.valueOf(entity.getAction()),
                entity.getCreatedAt()
        );
    }

    private QueryParts queryParts(String action, Integer minSeverity, String rule, String domain, boolean count) {
        List<String> predicates = new ArrayList<>();
        boolean evidenceJoin = rule != null && !rule.isBlank();
        if (action != null && !action.isBlank()) {
            predicates.add("e.action = :action");
        }
        if (minSeverity != null) {
            predicates.add("e.maxSeverity >= :minSeverity");
        }
        if (rule != null && !rule.isBlank()) {
            predicates.add("(LOWER(ev.ruleName) LIKE :rule OR LOWER(ev.ruleId) LIKE :rule)");
        }
        if (domain != null && !domain.isBlank()) {
            predicates.add("(LOWER(e.senderEmail) LIKE :domain OR LOWER(e.recipients) LIKE :domain)");
        }
        return new QueryParts(
                evidenceJoin ? "JOIN DlpScanEvidenceEntity ev ON ev.eventId = e.id " : "",
                predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates),
                action,
                minSeverity,
                rule == null || rule.isBlank() ? null : "%" + rule.toLowerCase() + "%",
                domain == null || domain.isBlank() ? null : "%" + domain.toLowerCase() + "%");
    }

    private void bind(Query query, QueryParts parts) {
        if (parts.action() != null && !parts.action().isBlank()) {
            query.setParameter("action", parts.action().toUpperCase());
        }
        if (parts.minSeverity() != null) {
            query.setParameter("minSeverity", parts.minSeverity());
        }
        if (parts.rule() != null) {
            query.setParameter("rule", parts.rule());
        }
        if (parts.domain() != null) {
            query.setParameter("domain", parts.domain());
        }
    }

    private String serialize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("DLP event list cannot be serialized", e);
        }
    }

    private List<String> deserialize(String values) {
        if (values == null || values.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(values, STRING_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("DLP event list is invalid: " + e.getMessage(), e);
        }
    }

    private MailDirection parseDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return null;
        }
        return MailDirection.valueOf(direction);
    }

    private record QueryParts(
            String join,
            String where,
            String action,
            Integer minSeverity,
            String rule,
            String domain
    ) {
    }
}
