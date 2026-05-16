package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailProcessing;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingRepository;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.infra.events.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Tracks pipeline step execution and persists results to MailProcessing aggregate.
 */
@Component
public class PipelineStepTracker {

    private static final Logger log = LoggerFactory.getLogger(PipelineStepTracker.class);

    private final MailProcessingRepository mailProcessingRepository;
    private final DomainEventPublisher domainEventPublisher;
    private static final Set<String> executedSteps = new HashSet<>();

    public PipelineStepTracker(MailProcessingRepository mailProcessingRepository,
                               DomainEventPublisher domainEventPublisher) {
        this.mailProcessingRepository = mailProcessingRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * Execute a pipeline step with tracking. Prevents duplicate execution.
     * @param message The mail message
     * @param step The pipeline step to execute
     * @return The pipeline result
     */
    public PipelineResult executeWithTracking(Message<byte[]> message, MailPipelineStep step) {
        String processingId = processingId(message);
        String stepName = step.getStepName();

        // Prevent duplicate execution for the same step and processingId
        if (processingId != null) {
            String stepKey = processingId + ":" + stepName;
            synchronized (executedSteps) {
                if (executedSteps.contains(stepKey)) {
                    log.info("=== SKIPPING DUPLICATE STEP: {} for processing: {} ===", stepName, processingId);
                    return PipelineResult.success(message.getPayload());
                }
                executedSteps.add(stepKey);

                // Clean up old entries periodically
                if (executedSteps.size() > 1000) {
                    executedSteps.clear();
                }
            }
        }

        log.info("=== EXECUTING STEP: {} for processing: {} ===", stepName, processingId);

        try {
            // Add step to processing record
            if (processingId != null) {
                mailProcessingRepository.findById(processingId).ifPresent(processing -> {
                    processing.addStep(stepName);
                    mailProcessingRepository.save(processing);
                });
            }

            // Execute step
            PipelineResult result = step.execute(message);
            if (result.events() != null && !result.events().isEmpty()) {
                result.events().forEach(domainEventPublisher::publishEvent);
            }

            // Mark step as completed
            if (processingId != null) {
                mailProcessingRepository.findById(processingId).ifPresent(processing -> {
                    processing.completeStep(stepName, result.success(),
                            result.success() ? null : result.quarantineReason());
                    mailProcessingRepository.save(processing);
                });
            }

            if (result.success()) {
                log.debug("Pipeline step completed successfully: {}", stepName);
            } else {
                log.warn("Pipeline step failed: {} - reason: {}",
                        stepName, result.quarantineReason());
            }

            return result;

        } catch (Exception e) {
            log.error("Pipeline step exception: {} - {}", stepName, e.getMessage(), e);

            // Mark step as failed
            if (processingId != null) {
                mailProcessingRepository.findById(processingId).ifPresent(processing -> {
                    processing.completeStep(stepName, false, e.getMessage());
                    mailProcessingRepository.save(processing);
                });
            }

            return PipelineResult.failure(e.getMessage());
        }
    }

    /**
     * Mark processing as completed with final result.
     */
    public void completeProcessing(String processingId, ProcessingResult result) {
        if (processingId == null) {
            return;
        }

        mailProcessingRepository.findById(processingId).ifPresent(processing -> {
            processing.completeProcessing(result);
            mailProcessingRepository.save(processing);
            log.info("Mail processing completed: {} with result: {}", processingId, result);
        });
    }

    /**
     * Get a wrapped step executor with tracking enabled.
     */
    public Function<Message<byte[]>, PipelineResult> wrap(MailPipelineStep step) {
        return message -> executeWithTracking(message, step);
    }

    private String processingId(Message<byte[]> message) {
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        if (value instanceof MailProcessingContext context) {
            return context.processingId();
        }
        return null;
    }
}
