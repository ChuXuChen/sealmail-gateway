package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.stereotype.Component;

@Component
public class MailErrorClassifier {

    public MailErrorClassification classify(Message<?> errorMessage) {
        Throwable error = error(errorMessage);
        Message<?> failedMessage = failedMessage(errorMessage, error);
        MailProcessingContext context = context(errorMessage);
        if (context == null && failedMessage != null) {
            context = context(failedMessage);
        }

        ClassifiedError errorClass = classify(error, context);
        MailProcessingContext classifiedContext = errorClass.context() != null ? errorClass.context() : context;
        ProcessingResult processingResult = errorClass.retryable()
                ? ProcessingResult.EXCEPTION
                : ProcessingResult.FAILED;
        MailErrorTarget target = target(errorClass.errorType(), classifiedContext);
        return new MailErrorClassification(
                errorClass.errorType(),
                errorClass.detail(),
                classifiedContext,
                errorClass.recordDisposition(),
                errorClass.retryable(),
                processingResult,
                errorClass.retryable() ? "MAIL_RETRY" : "MAIL_DEAD_LETTER",
                target,
                quarantineReason(classifiedContext, errorClass.errorType()),
                quarantineDetail(classifiedContext, errorClass));
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

    private MailErrorTarget target(MailProcessingErrorType errorType, MailProcessingContext context) {
        if (errorType == MailProcessingErrorType.QUARANTINE) {
            return MailErrorTarget.DEAD_LETTER;
        }
        if (context != null && context.originalMailContent().length > 0) {
            return MailErrorTarget.QUARANTINE;
        }
        return MailErrorTarget.DEAD_LETTER;
    }

    private String quarantineReason(MailProcessingContext context, MailProcessingErrorType errorType) {
        if (context != null && context.decision().quarantine() != null) {
            return context.decision().quarantine().reason();
        }
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
        if (context != null && context.decision().quarantine() != null) {
            return context.decision().quarantine().detail();
        }
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

    private record ClassifiedError(MailProcessingErrorType errorType,
                                   String detail,
                                   MailProcessingContext context,
                                   MailRecordDisposition recordDisposition,
                                   boolean retryable) {
    }
}
