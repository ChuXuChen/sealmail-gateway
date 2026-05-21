package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.dlp.DlpEvaluationResult;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.dlp.spi.DlpEvaluationPort;
import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.mail.pipeline.MailProcessingAuditEvents;
import com.sealmail.infra.mail.pipeline.MailProcessingMessages;
import com.sealmail.infra.mail.pipeline.MailProcessingStatusService;
import com.sealmail.infra.mail.pipeline.UnifiedMailDecisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DLP 检测步骤
 * 在邮件处理管道中执行数据防泄漏检测
 */
@Slf4j
@Component
public class DlpStep {

    private final DlpEvaluationPort dlpEvaluationPort;
    private final DomainEventPublisher domainEventPublisher;
    private final UnifiedMailDecisionService decisionService;
    private final MailProcessingStatusService statusService;

    public DlpStep(DlpEvaluationPort dlpEvaluationPort,
                   DomainEventPublisher domainEventPublisher,
                   UnifiedMailDecisionService decisionService,
                   MailProcessingStatusService statusService) {
        this.dlpEvaluationPort = dlpEvaluationPort;
        this.domainEventPublisher = domainEventPublisher;
        this.decisionService = decisionService;
        this.statusService = statusService;
    }

    public Message<byte[]> execute(Message<byte[]> message) {
        byte[] payload = message.getPayload();
        MailProcessingContext context = context(message);

        try {
            DlpEvaluationResult result = dlpEvaluationPort.evaluate(payload, context, true);
            String violationSummary = result.hasMatches() ? formatViolationSummary(result) : null;
            recordStatus(context, result, violationSummary);

            if (!result.hasMatches()) {
                log.debug("DLP scan passed, no violations found");
                return message;
            }

            log.warn("DLP scan found {} violations, highest severity: {}, final action: {}",
                    result.matches().size(), result.maxSeverity(), result.action());

            for (var match : result.matches()) {
                log.warn("  - [{}] {} (severity: {})",
                        match.rule().name(),
                        description(match.rule().name(), match.rule().description()),
                        match.rule().severity());
            }
            recordViolation(context, result);
            if (context == null && result.action() == DispositionAction.WARN) {
                return message;
            }

            MailProcessingContext updatedContext = decisionService.applyDlpEvaluation(
                    context,
                    result,
                    violationSummary);
            return MailProcessingMessages.withContext(message, updatedContext);

        } catch (Exception e) {
            log.error("DLP scan failed: {}", e.getMessage(), e);
            recordFailure(context, e.getMessage());
            throw new MailProcessingException(
                    MailProcessingErrorType.DLP,
                    "DLP scan failed: " + e.getMessage(),
                    context,
                    e);
        }
    }

    private String formatViolationSummary(DlpEvaluationResult result) {
        if (result.matches().size() <= 3) {
            return result.matches().stream()
                    .map(match -> description(match.rule().name(), match.rule().description()))
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");
        }
        return result.matches().stream()
                .limit(3)
                .map(match -> description(match.rule().name(), match.rule().description()))
                .reduce((a, b) -> a + "; " + b)
                .orElse("") + " and " + (result.matches().size() - 3) + " more";
    }

    private void recordViolation(MailProcessingContext context,
                                 DlpEvaluationResult result) {
        try {
            MailProcessingAuditEvents.publish(
                    domainEventPublisher,
                    AuditLogType.DLP_VIOLATION,
                    context,
                    "DLP_" + result.action().name(),
                    "action=" + result.action()
                            + ", recommendedAction=" + result.recommendedAction()
                            + ", severity=" + result.maxSeverity()
                            + ", eventId=" + result.eventId()
                            + ", ubaRisk=" + result.ubaRiskLevel()
                            + ", ubaActionUpgraded=" + result.ubaActionUpgraded()
                            + ", rules=" + String.join(",", ruleNames(result)));
        } catch (Exception e) {
            log.warn("Failed to record DLP audit log: {}", e.getMessage());
        }
    }

    private List<String> ruleNames(DlpEvaluationResult result) {
        return result.matches().stream()
                .limit(5)
                .map(match -> match.rule().name())
                .distinct()
                .toList();
    }

    private String description(String name, String description) {
        return description != null && !description.isBlank() ? description : "DLP rule matched: " + name;
    }

    public String getStepName() {
        return "dlp";
    }

    private MailProcessingContext context(Message<?> message) {
        return MailProcessingMessages.context(message);
    }

    private void recordStatus(MailProcessingContext context,
                              DlpEvaluationResult result,
                              String violationSummary) {
        if (statusService != null) {
            statusService.recordDlpEvaluation(context, result, violationSummary);
        }
    }

    private void recordFailure(MailProcessingContext context, String detail) {
        if (statusService != null) {
            statusService.recordDlpFailure(context, detail);
        }
    }
}
