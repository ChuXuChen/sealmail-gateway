package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.dlp.DlpScanResult;
import com.sealmail.domain.dlp.DlpViolation;
import com.sealmail.domain.mailsecurity.DlpDecision;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingDecision;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.shared.event.AuditEvent;
import com.sealmail.infra.dlp.DlpService;
import com.sealmail.infra.dlp.MimeContentExtractor;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.mail.pipeline.MailPipelineStep;
import com.sealmail.infra.mail.pipeline.PipelineResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * DLP 检测步骤
 * 在邮件处理管道中执行数据防泄漏检测
 */
@Slf4j
@Component
public class DlpStep implements MailPipelineStep {

    private final DlpService dlpService;
    private final MimeContentExtractor contentExtractor;
    private final DomainEventPublisher domainEventPublisher;

    public DlpStep(DlpService dlpService,
                   MimeContentExtractor contentExtractor,
                   DomainEventPublisher domainEventPublisher) {
        this.dlpService = dlpService;
        this.contentExtractor = contentExtractor;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    public PipelineResult execute(Message<byte[]> message) {
        byte[] payload = message.getPayload();

        try {
            MimeContentExtractor.ExtractedContent content = contentExtractor.extract(payload);
            MailProcessingContext context = context(message);
            MailEnvelope envelope = context != null ? context.envelope() : null;
            DlpScanResult result = dlpService.scan(content.subject(), content.body(), payload, envelope);

            if (!result.hasViolations()) {
                log.debug("DLP scan passed, no violations found");
                return PipelineResult.success(payload);
            }

            List<DlpViolation> violations = result.getViolations();
            log.warn("DLP scan found {} violations, highest severity: {}, final action: {}",
                    violations.size(), result.getMaxSeverity(), result.getFinalAction());

            for (DlpViolation violation : violations) {
                log.warn("  - [{}] {} (severity: {})",
                        violation.getRuleName(),
                        violation.getDescription(),
                        violation.getSeverity());
            }
            recordViolation(message, envelope, result);

            return switch (result.getFinalAction()) {
                case BLOCK -> PipelineResult.quarantine(
                        payload,
                        "POLICY_VIOLATION",
                        "DLP BLOCK: " + formatViolationSummary(violations),
                        MailRecordDisposition.EXCEPTION);
                case QUARANTINE -> PipelineResult.quarantine(
                        payload,
                        "POLICY_VIOLATION",
                        "DLP QUARANTINE: " + formatViolationSummary(violations),
                        MailRecordDisposition.DLP_QUARANTINE);
                case MUST_ENCRYPT -> {
                    if (context != null) {
                        MailProcessingDecision decision = context.decision()
                                .withMustEncrypt(true)
                                .withDlpDecision(new DlpDecision(
                                        result.getFinalAction(),
                                        result.getMaxSeverity(),
                                        result.getViolations().stream().map(DlpViolation::getRuleName).toList()));
                        MailProcessingContext updatedContext = context.withDecision(decision);
                        yield PipelineResult.successWithHeaders(payload, Map.of(
                                MailProcessingHeaders.CONTEXT, updatedContext));
                    }
                    yield PipelineResult.success(payload);
                }
                case WARN -> PipelineResult.success(payload);
            };

        } catch (Exception e) {
            log.error("DLP scan failed: {}", e.getMessage(), e);
            return PipelineResult.quarantine(
                    payload,
                    "SCAN_ERROR",
                    "DLP scan failed: " + e.getMessage(),
                    MailRecordDisposition.EXCEPTION);
        }
    }

    private String formatViolationSummary(List<DlpViolation> violations) {
        if (violations.size() <= 3) {
            return violations.stream()
                    .map(DlpViolation::getDescription)
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");
        }
        return violations.stream()
                .limit(3)
                .map(DlpViolation::getDescription)
                .reduce((a, b) -> a + "; " + b)
                .orElse("") + " and " + (violations.size() - 3) + " more";
    }

    private void recordViolation(Message<byte[]> message, MailEnvelope envelope, DlpScanResult result) {
        try {
            String messageId = envelope != null ? envelope.getMessageId() : message.getHeaders().getId().toString();
            domainEventPublisher.publishEvent(AuditEvent.builder()
                    .eventType(AuditLogType.DLP_VIOLATION.name())
                    .resourceType("EMAIL")
                    .resourceId(messageId)
                    .action("DLP_" + result.getFinalAction().name())
                    .description("action=" + result.getFinalAction()
                            + ", severity=" + result.getMaxSeverity()
                            + ", rules=" + ruleSummary(result.getViolations()))
                    .success(true)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to record DLP audit log: {}", e.getMessage());
        }
    }

    private String ruleSummary(List<DlpViolation> violations) {
        return violations.stream()
                .limit(5)
                .map(DlpViolation::getRuleName)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    @Override
    public String getStepName() {
        return "dlp";
    }

    private MailProcessingContext context(Message<?> message) {
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }
}
