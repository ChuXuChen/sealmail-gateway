package com.sealmail.infra.dlp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.dlp.DlpContentBundle;
import com.sealmail.domain.dlp.DlpMatch;
import com.sealmail.domain.dlp.DlpScanRequest;
import com.sealmail.domain.dlp.DlpUbaAssessment;
import com.sealmail.domain.dlp.DlpUbaRiskLevel;
import com.sealmail.domain.dlp.DlpUbaSenderRisk;
import com.sealmail.domain.dlp.spi.DlpUbaAnalyticsPort;
import com.sealmail.domain.dlp.spi.DlpUbaRiskEvaluator;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.persistence.entity.DlpUbaSenderBaselineEntity;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional
public class DlpUbaRiskService implements DlpUbaRiskEvaluator, DlpUbaAnalyticsPort {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public DlpUbaRiskService(EntityManager entityManager, ObjectMapper objectMapper) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public DlpUbaAssessment assess(DlpScanRequest request, List<DlpMatch> matches, DispositionAction currentAction) {
        if (request == null || request.context() == null || request.context().envelope() == null) {
            return DlpUbaAssessment.low(currentAction);
        }
        MailEnvelope envelope = request.context().envelope();
        String sender = normalizeEmail(envelope.getSender());
        if (sender.isBlank()) {
            return DlpUbaAssessment.low(currentAction);
        }

        DlpUbaSenderBaselineEntity baseline = entityManager.find(DlpUbaSenderBaselineEntity.class, sender);
        BaselineSnapshot snapshot = snapshot(baseline);
        Set<String> externalDomains = externalRecipientDomains(request);
        int hour = Instant.now().atZone(ZoneOffset.UTC).getHour();
        long maxAttachmentBytes = maxAttachmentBytes(request.content());

        List<String> reasons = new ArrayList<>();
        int score = 0;
        if (!externalDomains.isEmpty() && snapshot.outboundMessages() == 0) {
            score += 30;
            reasons.add("first outbound baseline for sender");
        }
        List<String> firstDomains = externalDomains.stream()
                .filter(domain -> !snapshot.externalDomains().contains(domain))
                .toList();
        if (!firstDomains.isEmpty() && snapshot.outboundMessages() >= 1) {
            score += Math.min(35, 15 + firstDomains.size() * 10);
            reasons.add("first external recipient domain: " + String.join(",", firstDomains));
        }
        if (snapshot.totalMessages() >= 5 && !snapshot.activeHours().contains(String.valueOf(hour))) {
            score += 15;
            reasons.add("unusual sending hour UTC=" + hour);
        }
        if (maxAttachmentBytes > 0 && snapshot.maxAttachmentBytes() > 0
                && maxAttachmentBytes >= snapshot.maxAttachmentBytes() * 3) {
            score += 20;
            reasons.add("attachment size exceeds sender baseline");
        }
        if (matches != null && !matches.isEmpty()) {
            score += Math.min(30, matches.size() * 10);
            if (snapshot.dlpHitCount() > 0) {
                score += 10;
                reasons.add("sender has previous DLP hits");
            }
            int maxSeverity = matches.stream().mapToInt(match -> match.rule().severity()).max().orElse(0);
            if (maxSeverity >= 8) {
                score += 20;
                reasons.add("high severity DLP match");
            }
        }

        DlpUbaRiskLevel riskLevel = score >= 70
                ? DlpUbaRiskLevel.HIGH
                : score >= 35 ? DlpUbaRiskLevel.MEDIUM : DlpUbaRiskLevel.LOW;
        DispositionAction upgraded = upgradedAction(currentAction, riskLevel, matches);
        boolean actionUpgraded = upgraded != currentAction;

        updateBaseline(sender, baseline, request, matches, riskLevel, reasons, externalDomains, hour, maxAttachmentBytes);
        return new DlpUbaAssessment(riskLevel, reasons, actionUpgraded, upgraded);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DlpUbaSenderRisk> listSenderRisks(int limit) {
        return entityManager.createQuery(
                        "SELECT b FROM DlpUbaSenderBaselineEntity b ORDER BY b.lastSeenAt DESC",
                        DlpUbaSenderBaselineEntity.class)
                .setMaxResults(Math.max(1, Math.min(500, limit <= 0 ? 50 : limit)))
                .getResultList()
                .stream()
                .map(this::toRisk)
                .toList();
    }

    private DispositionAction upgradedAction(DispositionAction currentAction,
                                             DlpUbaRiskLevel riskLevel,
                                             List<DlpMatch> matches) {
        DispositionAction action = currentAction != null ? currentAction : DispositionAction.WARN;
        if (matches == null || matches.isEmpty() || action == DispositionAction.BLOCK) {
            return action;
        }
        if (riskLevel == DlpUbaRiskLevel.HIGH
                && (action == DispositionAction.WARN || action == DispositionAction.MUST_ENCRYPT)) {
            return DispositionAction.QUARANTINE;
        }
        if (riskLevel == DlpUbaRiskLevel.MEDIUM && action == DispositionAction.WARN) {
            return DispositionAction.MUST_ENCRYPT;
        }
        return action;
    }

    private void updateBaseline(String sender,
                                DlpUbaSenderBaselineEntity baseline,
                                DlpScanRequest request,
                                List<DlpMatch> matches,
                                DlpUbaRiskLevel riskLevel,
                                List<String> reasons,
                                Set<String> externalDomains,
                                int hour,
                                long maxAttachmentBytes) {
        Instant now = Instant.now();
        DlpUbaSenderBaselineEntity entity = baseline != null ? baseline : new DlpUbaSenderBaselineEntity();
        if (baseline == null) {
            entity.setSenderEmail(sender);
            entity.setFirstSeenAt(now);
            entity.setTotalMessages(0);
            entity.setOutboundMessages(0);
            entity.setDlpHitCount(0);
            entity.setHighRiskCount(0);
            entity.setMaxAttachmentBytes(0);
        }
        Set<String> domains = new LinkedHashSet<>(deserialize(entity.getExternalDomains()));
        domains.addAll(externalDomains);
        Set<String> hours = new LinkedHashSet<>(deserialize(entity.getActiveHours()));
        hours.add(String.valueOf(hour));

        entity.setTotalMessages(entity.getTotalMessages() + 1);
        if (request.context().direction() == MailDirection.OUTBOUND) {
            entity.setOutboundMessages(entity.getOutboundMessages() + 1);
        }
        if (matches != null && !matches.isEmpty()) {
            entity.setDlpHitCount(entity.getDlpHitCount() + 1);
        }
        if (riskLevel == DlpUbaRiskLevel.HIGH) {
            entity.setHighRiskCount(entity.getHighRiskCount() + 1);
        }
        entity.setMaxAttachmentBytes(Math.max(entity.getMaxAttachmentBytes(), maxAttachmentBytes));
        entity.setExternalDomains(serialize(List.copyOf(domains)));
        entity.setActiveHours(serialize(List.copyOf(hours)));
        entity.setLastRiskLevel(riskLevel.name());
        entity.setLastRiskReasons(serialize(reasons));
        entity.setLastSeenAt(now);
        entity.setUpdatedAt(now);
        if (baseline == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
    }

    private DlpUbaSenderRisk toRisk(DlpUbaSenderBaselineEntity entity) {
        return new DlpUbaSenderRisk(
                entity.getSenderEmail(),
                entity.getTotalMessages(),
                entity.getOutboundMessages(),
                deserialize(entity.getExternalDomains()).size(),
                entity.getDlpHitCount(),
                entity.getHighRiskCount(),
                DlpUbaRiskLevel.valueOf(entity.getLastRiskLevel()),
                deserialize(entity.getLastRiskReasons()),
                entity.getFirstSeenAt(),
                entity.getLastSeenAt(),
                entity.getUpdatedAt()
        );
    }

    private BaselineSnapshot snapshot(DlpUbaSenderBaselineEntity entity) {
        if (entity == null) {
            return new BaselineSnapshot(0, 0, Set.of(), Set.of(), 0, 0);
        }
        return new BaselineSnapshot(
                entity.getTotalMessages(),
                entity.getOutboundMessages(),
                new LinkedHashSet<>(deserialize(entity.getExternalDomains())),
                new LinkedHashSet<>(deserialize(entity.getActiveHours())),
                entity.getMaxAttachmentBytes(),
                entity.getDlpHitCount());
    }

    private Set<String> externalRecipientDomains(DlpScanRequest request) {
        if (request.context().direction() != MailDirection.OUTBOUND) {
            return Set.of();
        }
        String senderDomain = request.context().envelope().getSender().getDomain();
        return request.context().envelope().getRecipients().stream()
                .map(EmailAddress::getDomain)
                .map(this::normalizeDomain)
                .filter(domain -> !domain.isBlank())
                .filter(domain -> !domain.equals(normalizeDomain(senderDomain)))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private long maxAttachmentBytes(DlpContentBundle content) {
        if (content == null) {
            return 0;
        }
        return content.parts().stream()
                .filter(part -> part.kind().name().startsWith("ATTACHMENT_"))
                .mapToLong(com.sealmail.domain.dlp.DlpContentPart::size)
                .max()
                .orElse(0);
    }

    private String normalizeEmail(EmailAddress address) {
        return address == null ? "" : address.getValue().trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDomain(String domain) {
        return domain == null ? "" : domain.trim().toLowerCase(Locale.ROOT).replaceAll("\\.+$", "");
    }

    private String serialize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("UBA list cannot be serialized", e);
        }
    }

    private List<String> deserialize(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private record BaselineSnapshot(
            long totalMessages,
            long outboundMessages,
            Set<String> externalDomains,
            Set<String> activeHours,
            long maxAttachmentBytes,
            long dlpHitCount
    ) {
    }
}
