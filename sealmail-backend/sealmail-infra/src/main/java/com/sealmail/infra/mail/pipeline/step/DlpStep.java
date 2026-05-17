package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.dlp.DlpEvaluationResult;
import com.sealmail.domain.mailsecurity.DlpDecision;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingDecision;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.dlp.spi.DlpEvaluationPort;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.mail.pipeline.MailProcessingAuditEvents;
import com.sealmail.infra.mail.pipeline.MailProcessingMessages;
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

    public DlpStep(DlpEvaluationPort dlpEvaluationPort,
                   DomainEventPublisher domainEventPublisher) {
        this.dlpEvaluationPort = dlpEvaluationPort;
        this.domainEventPublisher = domainEventPublisher;
    }

    public Message<byte[]> execute(Message<byte[]> message) {
        byte[] payload = message.getPayload();

        try {
            MailProcessingContext context = context(message);
            DlpEvaluationResult result = dlpEvaluationPort.evaluate(payload, context, true);

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

            return switch (result.action()) {
                case BLOCK -> MailProcessingMessages.quarantine(
                        withDlpDecision(message, context, result),
                        "POLICY_VIOLATION",
                        "DLP BLOCK: " + formatViolationSummary(result),
                        MailRecordDisposition.EXCEPTION);
                case QUARANTINE -> MailProcessingMessages.quarantine(
                        withDlpDecision(message, context, result),
                        "POLICY_VIOLATION",
                        "DLP QUARANTINE: " + formatViolationSummary(result),
                        MailRecordDisposition.DLP_QUARANTINE);
                case MUST_ENCRYPT -> {
                    if (context != null) {
                        MailProcessingDecision decision = context.decision()
                                .withMustEncrypt(true)
                                .withDlpDecision(decision(result));
                        MailProcessingContext updatedContext = context.withDecision(decision);
                        yield MailProcessingMessages.withContext(message, updatedContext);
                    }
                    yield message;
                }
                case WARN -> message;
            };

        } catch (Exception e) {
            log.error("DLP scan failed: {}", e.getMessage(), e);
            throw new MailProcessingException(
                    MailProcessingErrorType.DLP,
                    "DLP scan failed: " + e.getMessage(),
                    context(message),
                    e);
        }
    }

    private Message<byte[]> withDlpDecision(Message<byte[]> message,
                                            MailProcessingContext context,
                                            DlpEvaluationResult result) {
        if (context == null) {
            return message;
        }
        return MailProcessingMessages.withContext(
                message,
                context.withDecision(context.decision().withDlpDecision(decision(result))));
    }

    private DlpDecision decision(DlpEvaluationResult result) {
        return new DlpDecision(
                result.action(),
                result.maxSeverity(),
                ruleNames(result),
                result.eventId());
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
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }
}
