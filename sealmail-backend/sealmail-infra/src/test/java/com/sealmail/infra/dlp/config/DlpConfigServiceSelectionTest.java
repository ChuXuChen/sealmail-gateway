package com.sealmail.infra.dlp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.dlp.DlpScopeType;
import com.sealmail.domain.dlp.DlpRuleType;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.DlpPatternEntity;
import com.sealmail.infra.persistence.entity.DlpSelectionEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DlpConfigServiceSelectionTest {

    @Test
    void activePatternsForUsesOnlySelectedPatternIds() throws Exception {
        DlpConfigService service = serviceWithEntities(
                List.of(pattern("warn", DispositionAction.WARN), pattern("block", DispositionAction.BLOCK)),
                List.of(selection("[\"block\"]"))
        );

        List<DlpPatternConfig> patterns = service.activePatternsFor(envelope());

        assertEquals(1, patterns.size());
        assertEquals("block", patterns.getFirst().id());
    }

    @Test
    void activePatternsForUsesAllPatternsOnlyWhenSelectionPatternIdsAreNull() throws Exception {
        DlpConfigService service = serviceWithEntities(
                List.of(pattern("warn", DispositionAction.WARN), pattern("block", DispositionAction.BLOCK)),
                List.of(selection(null))
        );

        List<DlpPatternConfig> patterns = service.activePatternsFor(envelope());

        assertEquals(2, patterns.size());
    }

    @Test
    void legacyKeywordPatternIsReturnedAsEscapedPatternRule() throws Exception {
        DlpPatternEntity legacy = pattern("keyword", DispositionAction.WARN);
        legacy.setRuleType(DlpRuleType.KEYWORD.name());
        legacy.setRegex("project.alpha");
        DlpConfigService service = serviceWithEntities(List.of(legacy), List.of());

        DlpPatternConfig config = service.activePatternsFor(envelope()).getFirst();

        assertEquals(DlpRuleType.PATTERN, config.type());
        assertEquals("(?:\\Qproject.alpha\\E)", config.regex());
    }

    private static DlpConfigService serviceWithEntities(List<DlpPatternEntity> patterns,
                                                        List<DlpSelectionEntity> selections) throws Exception {
        EntityManager entityManager = mock(EntityManager.class);
        @SuppressWarnings("unchecked")
        TypedQuery<DlpPatternEntity> patternQuery = mock(TypedQuery.class);
        @SuppressWarnings("unchecked")
        TypedQuery<DlpSelectionEntity> selectionQuery = mock(TypedQuery.class);
        when(patternQuery.getResultList()).thenReturn(patterns);
        when(selectionQuery.getResultList()).thenReturn(selections);
        when(entityManager.createQuery(
                "SELECT p FROM DlpPatternEntity p WHERE p.enabled = true ORDER BY p.priority ASC, p.name ASC",
                DlpPatternEntity.class)).thenReturn(patternQuery);
        when(entityManager.createQuery(
                "SELECT s FROM DlpSelectionEntity s ORDER BY s.scopeType ASC, s.scopeValue ASC",
                DlpSelectionEntity.class)).thenReturn(selectionQuery);

        return new DlpConfigService(entityManager, mock(DomainEventPublisher.class), new ObjectMapper());
    }

    private static DlpPatternEntity pattern(String id, DispositionAction action) {
        DlpPatternEntity entity = new DlpPatternEntity();
        entity.setId(id);
        entity.setName(id);
        entity.setRegex(id + "_TOKEN");
        entity.setAction(action.name());
        entity.setSeverity(5);
        entity.setPriority(100);
        entity.setEnabled(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private static DlpSelectionEntity selection(String patternIds) {
        DlpSelectionEntity entity = new DlpSelectionEntity();
        entity.setId("selection-1");
        entity.setScopeType(DlpScopeType.GLOBAL.name());
        entity.setPatternIds(patternIds);
        entity.setEnabled(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private static MailEnvelope envelope() {
        return new MailEnvelope(
                "msg-1@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.net")),
                "127.0.0.1",
                "helo",
                Instant.now()
        );
    }
}
