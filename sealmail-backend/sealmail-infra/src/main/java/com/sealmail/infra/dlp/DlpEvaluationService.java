package com.sealmail.infra.dlp;

import com.sealmail.domain.dlp.DlpContentBundle;
import com.sealmail.domain.dlp.DlpDetectorResult;
import com.sealmail.domain.dlp.DlpEvaluationResult;
import com.sealmail.domain.dlp.DlpEvidence;
import com.sealmail.domain.dlp.DlpMatch;
import com.sealmail.domain.dlp.DlpPolicyResolution;
import com.sealmail.domain.dlp.DlpScanEvent;
import com.sealmail.domain.dlp.DlpScanRequest;
import com.sealmail.domain.dlp.DlpTestRequest;
import com.sealmail.domain.dlp.spi.DlpContentExtractor;
import com.sealmail.domain.dlp.spi.DlpDetector;
import com.sealmail.domain.dlp.spi.DlpEvaluationPort;
import com.sealmail.domain.dlp.spi.DlpEventRepository;
import com.sealmail.domain.dlp.spi.DlpEvidenceMasker;
import com.sealmail.domain.dlp.spi.DlpPolicyResolver;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.domain.shared.model.EmailAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class DlpEvaluationService implements DlpEvaluationPort {

    private final DlpContentExtractor contentExtractor;
    private final DlpPolicyResolver policyResolver;
    private final List<DlpDetector> detectors;
    private final DlpEvidenceMasker evidenceMasker;
    private final DlpEventRepository eventRepository;

    public DlpEvaluationService(DlpContentExtractor contentExtractor,
                                DlpPolicyResolver policyResolver,
                                List<DlpDetector> detectors,
                                DlpEvidenceMasker evidenceMasker,
                                DlpEventRepository eventRepository) {
        this.contentExtractor = contentExtractor;
        this.policyResolver = policyResolver;
        this.detectors = detectors == null ? List.of() : List.copyOf(detectors);
        this.evidenceMasker = evidenceMasker;
        this.eventRepository = eventRepository;
    }

    @Override
    @Transactional
    public DlpEvaluationResult evaluate(byte[] rawMail, MailProcessingContext context, boolean persistEvent) {
        long start = System.currentTimeMillis();
        DlpContentBundle content = contentExtractor.extract(rawMail, context);
        DlpPolicyResolution resolution = policyResolver.resolve(context, content);
        return evaluateResolved(content, context, resolution, persistEvent, start);
    }

    @Override
    @Transactional(readOnly = true)
    public DlpEvaluationResult test(DlpTestRequest request) {
        MailProcessingContext context = testContext(request);
        DlpContentBundle content = contentExtractor.extract(testPayload(request), context);
        DlpPolicyResolution resolution = policyResolver.resolve(context, content);
        return evaluateResolved(content, context, resolution, false, System.currentTimeMillis());
    }

    @Override
    @Transactional(readOnly = true)
    public DlpEvaluationResult simulatePolicy(String policyId, DlpTestRequest request) {
        MailProcessingContext context = testContext(request);
        DlpContentBundle content = contentExtractor.extract(testPayload(request), context);
        DlpPolicyResolution resolution = policyResolver.resolvePolicy(policyId, context, content);
        return evaluateResolved(content, context, resolution, false, System.currentTimeMillis());
    }

    private DlpEvaluationResult evaluateResolved(DlpContentBundle content,
                                                 MailProcessingContext context,
                                                 DlpPolicyResolution resolution,
                                                 boolean persistEvent,
                                                 long start) {
        DlpScanRequest scanRequest = new DlpScanRequest(content, resolution.rules(), resolution.policies(), context);
        List<DlpMatch> matches = new ArrayList<>();
        List<String> warnings = new ArrayList<>(content.allWarnings());
        for (DlpDetector detector : orderedDetectors()) {
            boolean relevant = resolution.rules().stream().anyMatch(rule -> detector.supports(rule.type()));
            if (!relevant) {
                continue;
            }
            try {
                DlpDetectorResult result = detector.detect(scanRequest);
                matches.addAll(result.matches());
                warnings.addAll(result.warnings());
            } catch (Exception e) {
                log.error("DLP detector {} failed: {}", detector.name(), e.getMessage(), e);
                warnings.add("Detector failed: " + detector.name());
                matches.add(scanErrorMatch(scanRequest, e));
            }
        }
        matches = thresholdMatches(matches);
        DispositionAction recommended = finalAction(matches);
        boolean monitorMode = resolution.monitorMode();
        DispositionAction action = monitorMode && recommended != DispositionAction.WARN
                ? DispositionAction.WARN
                : recommended;
        int maxSeverity = matches.stream().mapToInt(match -> match.rule().severity()).max().orElse(0);
        String eventId = persistEvent && !matches.isEmpty() ? UUID.randomUUID().toString() : null;
        List<DlpEvidence> evidence = eventId == null
                ? matches.stream().map(match -> evidenceMasker.mask("test", match)).toList()
                : matches.stream().map(match -> evidenceMasker.mask(eventId, match)).toList();
        long duration = System.currentTimeMillis() - start;

        if (persistEvent && !matches.isEmpty()) {
            DlpScanEvent event = event(eventId, context, resolution, action, maxSeverity, matches.size(), warnings, monitorMode, duration);
            eventRepository.save(event, evidence);
        }

        return new DlpEvaluationResult(
                eventId,
                action,
                recommended,
                maxSeverity,
                matches,
                evidence,
                warnings.stream().distinct().toList(),
                resolution.policyIds(),
                resolution.ruleGroupIds(),
                monitorMode,
                duration);
    }

    private DlpScanEvent event(String eventId,
                               MailProcessingContext context,
                               DlpPolicyResolution resolution,
                               DispositionAction action,
                               int maxSeverity,
                               int matchCount,
                               List<String> warnings,
                               boolean monitorMode,
                               long duration) {
        MailEnvelope envelope = context != null ? context.envelope() : null;
        return new DlpScanEvent(
                eventId,
                envelope != null ? envelope.getMessageId() : null,
                context != null ? context.processingId() : null,
                context != null ? context.direction() : null,
                envelope != null ? envelope.getSender().getValue() : null,
                envelope != null ? envelope.getRecipients().stream().map(EmailAddress::getValue).toList() : List.of(),
                context != null ? context.subject() : null,
                remoteAddress(context),
                resolution.policyIds(),
                resolution.ruleGroupIds(),
                action,
                maxSeverity,
                matchCount,
                warnings.stream().distinct().toList(),
                monitorMode,
                duration,
                null,
                false,
                null,
                null,
                null,
                Instant.now()
        );
    }

    private List<DlpDetector> orderedDetectors() {
        return detectors.stream()
                .sorted(Comparator.comparingInt(DlpDetector::priority).thenComparing(DlpDetector::name))
                .toList();
    }

    private List<DlpMatch> thresholdMatches(List<DlpMatch> matches) {
        Map<String, List<DlpMatch>> byRule = new LinkedHashMap<>();
        for (DlpMatch match : matches) {
            byRule.computeIfAbsent(match.rule().id(), ignored -> new ArrayList<>()).add(match);
        }
        List<DlpMatch> result = new ArrayList<>();
        for (List<DlpMatch> ruleMatches : byRule.values()) {
            if (ruleMatches.size() < ruleMatches.getFirst().rule().minMatchCount()) {
                continue;
            }
            result.addAll(ruleMatches.stream()
                    .limit(ruleMatches.getFirst().rule().maxEvidenceCount())
                    .toList());
        }
        result.sort(Comparator.comparingInt((DlpMatch match) -> actionPriority(match.rule().defaultAction())).reversed()
                .thenComparing(Comparator.comparingInt((DlpMatch match) -> match.rule().severity()).reversed())
                .thenComparing(match -> match.rule().name())
                .thenComparing(DlpMatch::startOffset));
        return result;
    }

    private DispositionAction finalAction(List<DlpMatch> matches) {
        return matches.stream()
                .map(match -> match.rule().defaultAction())
                .max(Comparator.comparingInt(this::actionPriority))
                .orElse(DispositionAction.WARN);
    }

    private int actionPriority(DispositionAction action) {
        return switch (action) {
            case BLOCK -> 4;
            case QUARANTINE -> 3;
            case MUST_ENCRYPT -> 2;
            case WARN -> 1;
        };
    }

    private DlpMatch scanErrorMatch(DlpScanRequest request, Exception e) {
        var rule = new com.sealmail.domain.dlp.DlpRule(
                "scan-error",
                "DLP_SCAN_ERROR",
                "DLP detector failed",
                com.sealmail.domain.dlp.DlpRuleType.KEYWORD,
                "scan-error",
                null,
                List.of(),
                1,
                1,
                com.sealmail.domain.dlp.DlpMaskingStrategy.FULL,
                0,
                DispositionAction.BLOCK,
                10,
                true,
                null,
                null);
        var part = request.content().parts().isEmpty()
                ? new com.sealmail.domain.dlp.DlpContentPart(
                "scan-error",
                com.sealmail.domain.dlp.DlpContentKind.HEADERS,
                null,
                "text/plain",
                0,
                e.getMessage(),
                false,
                List.of())
                : request.content().parts().getFirst();
        return new DlpMatch(rule, part, e.getMessage(), 0, e.getMessage() != null ? e.getMessage().length() : 0);
    }

    private MailProcessingContext testContext(DlpTestRequest request) {
        String sender = request != null && request.sender() != null && !request.sender().isBlank()
                ? request.sender()
                : "sender@example.com";
        List<String> recipients = request != null && request.recipients() != null && !request.recipients().isEmpty()
                ? request.recipients()
                : List.of("recipient@example.net");
        MailEnvelope envelope = new MailEnvelope(
                "dlp-test-" + UUID.randomUUID() + "@local.test",
                new EmailAddress(sender),
                recipients.stream().map(EmailAddress::new).toList(),
                "127.0.0.1",
                "dlp-test",
                Instant.now(),
                testPayload(request));
        return MailProcessingContext.create(envelope)
                .withDirection(request != null && request.direction() != null ? request.direction() : MailDirection.OUTBOUND)
                .withSubject(request != null && request.subject() != null ? request.subject() : "");
    }

    private byte[] testPayload(DlpTestRequest request) {
        String subject = request != null && request.subject() != null ? request.subject() : "";
        String body = request != null && request.body() != null ? request.body() : "";
        return ("Subject: " + subject + "\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n" + body)
                .getBytes(StandardCharsets.UTF_8);
    }

    private String remoteAddress(MailProcessingContext context) {
        if (context == null) {
            return null;
        }
        if (context.auditTrace() != null && context.auditTrace().remoteAddress() != null) {
            return context.auditTrace().remoteAddress();
        }
        return context.envelope().getRemoteHost();
    }
}
