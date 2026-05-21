package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.dlp.DlpEvaluationResult;
import com.sealmail.domain.mailauth.AuthenticationResultSet;
import com.sealmail.domain.mailauth.MailAuthDecision;
import com.sealmail.domain.mailsecurity.DlpDecision;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingDecision;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UnifiedMailDecisionService {

    public MailProcessingContext applyMailAuthentication(MailProcessingContext context,
                                                         AuthenticationResultSet result) {
        MailProcessingContext updated = requireContext(context, "mail authentication")
                .withMailAuthResults(result);
        MailAuthDecision decision = result.decision();
        if (decision.requiresQuarantine()) {
            return markQuarantine(
                    updated,
                    decision.reason(),
                    decision.detail(),
                    MailRecordDisposition.EXCEPTION);
        }
        return updated;
    }

    public MailProcessingContext applyDlpEvaluation(MailProcessingContext context,
                                                    DlpEvaluationResult result,
                                                    String violationSummary) {
        MailProcessingContext updated = requireContext(context, "DLP evaluation");
        DlpDecision dlpDecision = new DlpDecision(
                result.action(),
                result.maxSeverity(),
                ruleNames(result),
                result.eventId());
        MailProcessingDecision decision = updated.decision().withDlpDecision(dlpDecision);
        updated = updated.withDecision(decision);

        return switch (result.action()) {
            case BLOCK -> markQuarantine(
                    updated,
                    "POLICY_VIOLATION",
                    "DLP BLOCK: " + violationSummary,
                    MailRecordDisposition.EXCEPTION);
            case QUARANTINE -> markQuarantine(
                    updated,
                    "POLICY_VIOLATION",
                    "DLP QUARANTINE: " + violationSummary,
                    MailRecordDisposition.DLP_QUARANTINE);
            case MUST_ENCRYPT -> updated.withDecision(updated.decision().withMustEncrypt(true));
            case WARN -> updated;
        };
    }

    public MailProcessingContext markQuarantine(MailProcessingContext context,
                                                String reason,
                                                String detail,
                                                MailRecordDisposition disposition) {
        MailProcessingContext current = requireContext(context, "quarantine decision");
        MailProcessingDecision decision = current.decision().withQuarantine(reason, detail);
        return current
                .withDecision(decision)
                .withRecordDisposition(disposition != null ? disposition : MailRecordDisposition.EXCEPTION);
    }

    public boolean requiresQuarantine(MailProcessingContext context) {
        return context != null && context.decision().requiresQuarantine();
    }

    public String quarantineSummary(MailProcessingContext context) {
        if (context == null || context.decision().quarantine() == null) {
            return "Mail processing requested quarantine";
        }
        return "quarantineReason=" + context.decision().quarantine().reason()
                + MailProcessingAuditEvents.detailPresence(context.decision().quarantine().detail());
    }

    private MailProcessingContext requireContext(MailProcessingContext context, String stage) {
        if (context == null) {
            throw new MailProcessingException(
                    MailProcessingErrorType.PIPELINE,
                    "Mail processing context not found while applying " + stage,
                    null);
        }
        return context;
    }

    private List<String> ruleNames(DlpEvaluationResult result) {
        return result.matches().stream()
                .limit(5)
                .map(match -> match.rule().name())
                .distinct()
                .toList();
    }
}
