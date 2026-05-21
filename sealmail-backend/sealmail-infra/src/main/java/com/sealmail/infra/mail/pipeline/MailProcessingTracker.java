package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailProcessingRepository;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class MailProcessingTracker {

    private static final Logger log = LoggerFactory.getLogger(MailProcessingTracker.class);

    private final MailProcessingRepository mailProcessingRepository;
    private final MailProcessingStatusService statusService;

    public MailProcessingTracker(MailProcessingRepository mailProcessingRepository,
                                 MailProcessingStatusService statusService) {
        this.mailProcessingRepository = mailProcessingRepository;
        this.statusService = statusService;
    }

    public Message<byte[]> executeStep(Message<byte[]> message,
                                       String stepName,
                                       MailProcessingErrorType errorType,
                                       StepAction action) {
        String processingId = processingId(message);
        log.info("=== EXECUTING STEP: {} for processing: {} ===", stepName, processingId);
        addStep(processingId, stepName);

        try {
            Message<byte[]> result = action.apply(message);
            if (result == null) {
                throw new MailProcessingException(
                        errorType,
                        "Mail processing step returned no message",
                        context(message));
            }

            boolean quarantined = !"quarantine".equals(stepName) && requiresQuarantine(result);
            completeStep(processingId, stepName, !quarantined, quarantined ? failureSummary(result) : null);
            return result;
        } catch (Exception e) {
            completeStep(processingId, stepName, false, exceptionSummary(e));
            if (e instanceof MailProcessingException mailProcessingException) {
                throw mailProcessingException;
            }
            throw new MailProcessingException(errorType, e.getMessage(), context(message), e);
        }
    }

    public void completeProcessing(String processingId, ProcessingResult result) {
        completeProcessing(processingId, result, null);
    }

    public void completeProcessing(String processingId, ProcessingResult result, MailProcessingContext context) {
        if (processingId == null) {
            return;
        }
        mailProcessingRepository.findById(processingId).ifPresent(processing -> {
            processing.completeProcessing(result);
            mailProcessingRepository.save(processing);
            log.info("Mail processing completed: {} with result: {}", processingId, result);
        });
        if (statusService != null) {
            statusService.recordProcessingResult(processingId, result, context);
        }
    }

    private void addStep(String processingId, String stepName) {
        if (processingId == null) {
            return;
        }
        mailProcessingRepository.findById(processingId).ifPresent(processing -> {
            processing.addStep(stepName);
            mailProcessingRepository.save(processing);
        });
    }

    private void completeStep(String processingId, String stepName, boolean success, String failureReason) {
        if (processingId == null) {
            return;
        }
        mailProcessingRepository.findById(processingId).ifPresent(processing -> {
            processing.completeStep(stepName, success, success ? null : failureReason);
            mailProcessingRepository.save(processing);
        });
    }

    private boolean requiresQuarantine(Message<byte[]> message) {
        MailProcessingContext context = context(message);
        return context != null && context.decision().requiresQuarantine();
    }

    private String failureSummary(Message<byte[]> message) {
        MailProcessingContext context = context(message);
        if (context == null || context.decision().quarantine() == null) {
            return "Mail processing step requested quarantine";
        }
        return "quarantineReason=" + context.decision().quarantine().reason()
                + MailProcessingAuditEvents.detailPresence(context.decision().quarantine().detail());
    }

    private String exceptionSummary(Exception error) {
        if (error instanceof MailProcessingException mailProcessingException) {
            return "errorType=" + mailProcessingException.errorType().name()
                    + ", retryable=" + mailProcessingException.retryable()
                    + MailProcessingAuditEvents.detailPresence(mailProcessingException.getMessage());
        }
        String errorType = error != null ? error.getClass().getSimpleName() : "Unknown";
        String message = error != null ? error.getMessage() : null;
        return "errorType=" + errorType + MailProcessingAuditEvents.detailPresence(message);
    }

    private String processingId(Message<byte[]> message) {
        MailProcessingContext context = context(message);
        return context != null ? context.processingId() : null;
    }

    private MailProcessingContext context(Message<?> message) {
        return MailProcessingMessages.context(message);
    }

    @FunctionalInterface
    public interface StepAction {
        Message<byte[]> apply(Message<byte[]> message) throws Exception;
    }
}
