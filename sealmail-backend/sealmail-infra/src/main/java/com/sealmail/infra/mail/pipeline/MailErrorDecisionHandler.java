package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingDecision;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailProcessingRepository;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.domain.shared.event.AuditEvent;
import com.sealmail.infra.events.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
public class MailErrorDecisionHandler {

    private static final Logger log = LoggerFactory.getLogger(MailErrorDecisionHandler.class);

    private final MailProcessingRepository mailProcessingRepository;
    private final DomainEventPublisher domainEventPublisher;

    public MailErrorDecisionHandler(MailProcessingRepository mailProcessingRepository,
                                    DomainEventPublisher domainEventPublisher) {
        this.mailProcessingRepository = mailProcessingRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    public ErrorDecision decide(Message<?> errorMessage) {
        Throwable error = error(errorMessage);
        Message<?> failedMessage = failedMessage(errorMessage, error);
        MailProcessingContext context = context(errorMessage);
        if (context == null && failedMessage != null) {
            context = context(failedMessage);
        }
        ClassifiedError classified = classify(error, context);
        MailProcessingContext classifiedContext = classified.context() != null ? classified.context() : context;

        updateProcessing(classifiedContext, classified);
        recordAudit(classifiedContext, classified);

        if (classified.errorType() == MailProcessingErrorType.QUARANTINE) {
            return ErrorDecision.deadLetter();
        }
        if (classifiedContext != null && classifiedContext.originalMailContent().length > 0) {
            return ErrorDecision.quarantine(quarantineMessage(classifiedContext, classified));
        }
        return ErrorDecision.deadLetter();
    }

    private Throwable error(Message<?> message) {
        Object payload = message.getPayload();
        return payload instanceof Throwable throwable ? throwable : null;
    }

    private Message<?> failedMessage(Message<?> errorMessage, Throwable error) {
        if (errorMessage instanceof ErrorMessage springErrorMessage
                && springErrorMessage.getOriginalMessage() != null) {
            return springErrorMessage.getOriginalMessage();
        }
        if (error instanceof MessagingException messagingException) {
            return messagingException.getFailedMessage();
        }
        return null;
    }

    private ClassifiedError classify(Throwable error, MailProcessingContext context) {
        Throwable root = root(error);
        if (root instanceof MailProcessingException mailProcessingException) {
            return new ClassifiedError(
                    mailProcessingException.errorType(),
                    detail(mailProcessingException),
                    mailProcessingException.context() != null ? mailProcessingException.context() : context,
                    mailProcessingException.recordDisposition(),
                    mailProcessingException.retryable());
        }
        return new ClassifiedError(
                MailProcessingErrorType.UNKNOWN,
                detail(error),
                context,
                MailRecordDisposition.EXCEPTION,
                false);
    }

    private Throwable root(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) {
            if (current instanceof MailProcessingException) {
                return current;
            }
            current = current.getCause();
        }
        return current != null ? current : error;
    }

    private String detail(Throwable error) {
        if (error == null) {
            return "Unknown mail processing error";
        }
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message;
    }

    private void updateProcessing(MailProcessingContext context, ClassifiedError error) {
        if (context == null || context.processingId() == null) {
            return;
        }
        try {
            mailProcessingRepository.findById(context.processingId()).ifPresent(processing -> {
                processing.completeProcessing(error.retryable() ? ProcessingResult.EXCEPTION : ProcessingResult.FAILED);
                mailProcessingRepository.save(processing);
            });
        } catch (Exception e) {
            log.warn("Failed to update mail processing error state for {}: {}",
                    context.processingId(), e.getMessage());
        }
    }

    private void recordAudit(MailProcessingContext context, ClassifiedError error) {
        try {
            domainEventPublisher.publishEvent(AuditEvent.builder()
                    .eventType(AuditLogType.EMAIL_QUARANTINED.name())
                    .resourceType("EMAIL")
                    .resourceId(context != null ? context.envelope().getMessageId() : null)
                    .action(error.retryable() ? "MAIL_RETRY" : "MAIL_DEAD_LETTER")
                    .description("processingId=" + processingId(context)
                            + ", errorType=" + error.errorType().name()
                            + ", detail=" + error.detail())
                    .success(false)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to record mail error audit event: {}", e.getMessage());
        }
    }

    private Message<byte[]> quarantineMessage(MailProcessingContext context, ClassifiedError error) {
        String reason = context.decision().quarantine() != null
                ? context.decision().quarantine().reason()
                : quarantineReason(error.errorType());
        String detail = context.decision().quarantine() != null
                ? context.decision().quarantine().detail()
                : quarantineDetail(context, error);
        MailProcessingContext quarantineContext = context
                .withDecision(MailProcessingDecision.none().withQuarantine(
                        reason,
                        detail))
                .withRecordDisposition(error.recordDisposition());
        return MessageBuilder.withPayload(context.originalMailContent())
                .setHeader(MailProcessingHeaders.CONTEXT, quarantineContext)
                .build();
    }

    private String quarantineReason(MailProcessingErrorType errorType) {
        return switch (errorType) {
            case AUTHENTICATION -> "EMAIL_AUTH_FAILED";
            case DECRYPTION -> "DECRYPTION_FAILED";
            case VERIFICATION -> "SIGNATURE_INVALID";
            case DLP -> "SCAN_ERROR";
            case ENCRYPTION -> "ENCRYPTION_FAILED";
            case ROUTING -> "DOMAIN_NOT_CONFIGURED";
            case RELAY, QUARANTINE, PIPELINE, UNKNOWN, SIGNING, DKIM_SIGNING -> "POLICY_VIOLATION";
        };
    }

    private String quarantineDetail(MailProcessingContext context, ClassifiedError error) {
        return "processingId=" + processingId(context)
                + "; errorType=" + error.errorType().name()
                + "; detail=" + error.detail();
    }

    private String processingId(MailProcessingContext context) {
        return context != null && context.processingId() != null ? context.processingId() : "unknown";
    }

    private MailProcessingContext context(Message<?> message) {
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }

    public record ErrorDecision(boolean quarantine, Message<byte[]> quarantineMessage) {
        public static ErrorDecision quarantine(Message<byte[]> message) {
            return new ErrorDecision(true, message);
        }

        public static ErrorDecision deadLetter() {
            return new ErrorDecision(false, null);
        }
    }

    private record ClassifiedError(MailProcessingErrorType errorType,
                                   String detail,
                                   MailProcessingContext context,
                                   MailRecordDisposition recordDisposition,
                                   boolean retryable) {
    }
}
