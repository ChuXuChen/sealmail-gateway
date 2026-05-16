package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailProcessing;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingRepository;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.infra.events.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Tracks pipeline step execution and persists results to MailProcessing aggregate.
 */
@Component
public class PipelineStepTracker {

    private static final Logger log = LoggerFactory.getLogger(PipelineStepTracker.class);

    private final MailProcessingRepository mailProcessingRepository;
    private final DomainEventPublisher domainEventPublisher;

    public PipelineStepTracker(MailProcessingRepository mailProcessingRepository,
                               DomainEventPublisher domainEventPublisher) {
        this.mailProcessingRepository = mailProcessingRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * Execute a pipeline step with processing-state tracking.
     * @param message The mail message
     * @param step The pipeline step to execute
     * @return The pipeline result
     */
    public PipelineResult executeWithTracking(Message<byte[]> message, MailPipelineStep step) {
        String processingId = processingId(message);
        String stepName = step.getStepName();

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
            PipelineResult rawResult = step.execute(message);
            PipelineResult result = rawResult != null
                    ? rawResult
                    : PipelineResult.failure("Pipeline step returned no result");
            if (result.events() != null && !result.events().isEmpty()) {
                result.events().forEach(domainEventPublisher::publishEvent);
            }

            // Mark step as completed
            if (processingId != null) {
                mailProcessingRepository.findById(processingId).ifPresent(processing -> {
                    processing.completeStep(stepName, result.success(),
                            result.success() ? null : failureReason(result));
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

    public Message<byte[]> executeMessageWithTracking(Message<byte[]> message, MailPipelineStep step) {
        return toMessage(message, executeWithTracking(message, step));
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

    public Function<Message<byte[]>, Message<byte[]>> wrapMessage(MailPipelineStep step) {
        return message -> executeMessageWithTracking(message, step);
    }

    private Message<byte[]> toMessage(Message<byte[]> original, PipelineResult result) {
        PipelineResult safeResult = result != null
                ? result
                : PipelineResult.failure("Pipeline step returned no result");
        byte[] payload = payload(original, safeResult);
        MessageBuilder<byte[]> builder = MessageBuilder.withPayload(payload)
                .copyHeaders(original.getHeaders());
        if (safeResult.headers() != null) {
            safeResult.headers().forEach((name, value) -> {
                if (name != null && value != null) {
                    builder.setHeader(name, value);
                }
            });
        }
        if (!safeResult.success()) {
            MailProcessingContext context = resultContext(original, safeResult);
            if (context != null) {
                builder.setHeader(MailProcessingHeaders.CONTEXT, quarantineContext(context, safeResult));
            }
        }
        return builder.build();
    }

    private byte[] payload(Message<byte[]> original, PipelineResult result) {
        if (result.payload() != null && result.payload().length > 0) {
            return result.payload();
        }
        if (!result.success()) {
            MailProcessingContext context = resultContext(original, result);
            if (context != null && context.originalMailContent().length > 0) {
                return context.originalMailContent();
            }
        }
        return original.getPayload();
    }

    private MailProcessingContext resultContext(Message<byte[]> original, PipelineResult result) {
        if (result.headers() != null) {
            Object value = result.headers().get(MailProcessingHeaders.CONTEXT);
            if (value instanceof MailProcessingContext context) {
                return context;
            }
        }
        return context(original);
    }

    private MailProcessingContext quarantineContext(MailProcessingContext context, PipelineResult result) {
        MailProcessingContext updated = context
                .withDecision(context.decision().withQuarantine(
                        quarantineReason(result),
                        quarantineDetail(result)))
                .withRecordDisposition(recordDisposition(context, result));
        return updated;
    }

    private String quarantineReason(PipelineResult result) {
        if (result.quarantineReason() != null && !result.quarantineReason().isBlank()) {
            return result.quarantineReason();
        }
        return "POLICY_VIOLATION";
    }

    private String quarantineDetail(PipelineResult result) {
        if (result.quarantineDetail() != null && !result.quarantineDetail().isBlank()) {
            return result.quarantineDetail();
        }
        if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
            return result.errorMessage();
        }
        return quarantineReason(result);
    }

    private com.sealmail.domain.mailsecurity.MailRecordDisposition recordDisposition(
            MailProcessingContext context,
            PipelineResult result) {
        if (result.recordDisposition() != null) {
            return result.recordDisposition();
        }
        if (context.recordDisposition() != null) {
            return context.recordDisposition();
        }
        return com.sealmail.domain.mailsecurity.MailRecordDisposition.EXCEPTION;
    }

    private String failureReason(PipelineResult result) {
        if (result.quarantineReason() != null && !result.quarantineReason().isBlank()) {
            return result.quarantineReason();
        }
        if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
            return result.errorMessage();
        }
        return result.quarantineDetail();
    }

    private String processingId(Message<byte[]> message) {
        MailProcessingContext context = context(message);
        return context != null ? context.processingId() : null;
    }

    private MailProcessingContext context(Message<?> message) {
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }
}
