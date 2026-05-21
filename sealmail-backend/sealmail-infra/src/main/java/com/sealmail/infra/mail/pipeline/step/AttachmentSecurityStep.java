package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.mailsecurity.AttachmentSecurityAction;
import com.sealmail.domain.mailsecurity.AttachmentSecurityFinding;
import com.sealmail.domain.mailsecurity.AttachmentSecurityResult;
import com.sealmail.domain.mailsecurity.MailInspectionBundle;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.infra.mail.inspection.MailInspectionService;
import com.sealmail.infra.mail.pipeline.AttachmentSecurityService;
import com.sealmail.infra.mail.pipeline.MailProcessingMessages;
import com.sealmail.infra.mail.pipeline.MailProcessingStatusService;
import com.sealmail.infra.mail.pipeline.UnifiedMailDecisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class AttachmentSecurityStep {

    private final MailInspectionService inspectionService;
    private final AttachmentSecurityService attachmentSecurityService;
    private final UnifiedMailDecisionService decisionService;
    private final MailProcessingStatusService statusService;

    public AttachmentSecurityStep(MailInspectionService inspectionService,
                                  AttachmentSecurityService attachmentSecurityService,
                                  UnifiedMailDecisionService decisionService,
                                  MailProcessingStatusService statusService) {
        this.inspectionService = inspectionService;
        this.attachmentSecurityService = attachmentSecurityService;
        this.decisionService = decisionService;
        this.statusService = statusService;
    }

    public Message<byte[]> execute(Message<byte[]> message) {
        MailProcessingContext context = context(message);
        if (context == null) {
            throw new MailProcessingException(
                    MailProcessingErrorType.ATTACHMENT_SECURITY,
                    "Mail processing context not found in message headers",
                    null);
        }

        try {
            MailInspectionBundle bundle = context.inspectionBundle() != null
                    ? context.inspectionBundle()
                    : inspectionService.inspect(message.getPayload(), context);
            AttachmentSecurityResult result = attachmentSecurityService.evaluate(bundle);
            MailProcessingContext enrichedContext = context.withInspectionBundle(bundle);
            String summary = summary(result);
            recordStatus(enrichedContext, result, summary);

            if (result.action() == AttachmentSecurityAction.ALLOW
                    || result.action() == AttachmentSecurityAction.WARN) {
                if (result.hasFindings()) {
                    log.warn("Attachment security found {} findings, highest severity: {}, final action: {}",
                            result.findings().size(), result.maxSeverity(), result.action());
                }
                return MailProcessingMessages.withContext(message, enrichedContext);
            }

            MailProcessingContext updatedContext = decisionService.applyAttachmentSecurity(enrichedContext, result, summary);
            log.warn("Attachment security found {} findings, highest severity: {}, final action: {}",
                    result.findings().size(), result.maxSeverity(), result.action());
            for (AttachmentSecurityFinding finding : result.findings()) {
                log.warn("  - [{}] {} (severity: {})",
                        finding.code(),
                        finding.message(),
                        finding.severity());
            }
            return MailProcessingMessages.withContext(message, updatedContext);

        } catch (Exception e) {
            if (e instanceof MailProcessingException mailProcessingException) {
                recordFailure(
                        mailProcessingException.context() != null ? mailProcessingException.context() : context,
                        mailProcessingException.getMessage());
                throw mailProcessingException;
            }
            log.error("Attachment security scan failed: {}", e.getMessage(), e);
            recordFailure(context, e.getMessage());
            throw new MailProcessingException(
                    MailProcessingErrorType.ATTACHMENT_SECURITY,
                    "Attachment security scan failed: " + e.getMessage(),
                    context,
                    e);
        }
    }

    public String getStepName() {
        return "attachment-security";
    }

    private String summary(AttachmentSecurityResult result) {
        if (result == null) {
            return "Attachment security result unavailable";
        }
        List<String> findings = result.findings().stream()
                .limit(3)
                .map(finding -> finding.code() + ": " + finding.message())
                .toList();
        if (!findings.isEmpty()) {
            String joined = String.join("; ", findings);
            return result.findings().size() > 3
                    ? joined + " and " + (result.findings().size() - 3) + " more"
                    : joined;
        }
        List<String> warnings = result.warnings().stream()
                .limit(3)
                .toList();
        if (!warnings.isEmpty()) {
            return String.join("; ", warnings);
        }
        return "Attachment security passed";
    }

    private void recordStatus(MailProcessingContext context,
                              AttachmentSecurityResult result,
                              String summary) {
        if (statusService != null) {
            statusService.recordAttachmentSecurity(context, result, summary);
        }
    }

    private void recordFailure(MailProcessingContext context, String detail) {
        if (statusService != null) {
            statusService.recordAttachmentSecurityFailure(context, detail);
        }
    }

    private MailProcessingContext context(Message<?> message) {
        return MailProcessingMessages.context(message);
    }
}
