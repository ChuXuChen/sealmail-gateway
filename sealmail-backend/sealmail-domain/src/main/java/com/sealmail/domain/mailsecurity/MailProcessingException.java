package com.sealmail.domain.mailsecurity;

public class MailProcessingException extends RuntimeException {

    private final MailProcessingErrorType errorType;
    private final MailProcessingContext context;
    private final MailRecordDisposition recordDisposition;
    private final boolean retryable;

    public MailProcessingException(MailProcessingErrorType errorType,
                                   String message,
                                   MailProcessingContext context) {
        this(errorType, message, context, MailRecordDisposition.EXCEPTION, false, null);
    }

    public MailProcessingException(MailProcessingErrorType errorType,
                                   String message,
                                   MailProcessingContext context,
                                   Throwable cause) {
        this(errorType, message, context, MailRecordDisposition.EXCEPTION, false, cause);
    }

    public MailProcessingException(MailProcessingErrorType errorType,
                                   String message,
                                   MailProcessingContext context,
                                   MailRecordDisposition recordDisposition,
                                   boolean retryable,
                                   Throwable cause) {
        super(message, cause);
        this.errorType = errorType != null ? errorType : MailProcessingErrorType.UNKNOWN;
        this.context = context;
        this.recordDisposition = recordDisposition != null ? recordDisposition : MailRecordDisposition.EXCEPTION;
        this.retryable = retryable;
    }

    public MailProcessingErrorType errorType() {
        return errorType;
    }

    public MailProcessingContext context() {
        return context;
    }

    public MailRecordDisposition recordDisposition() {
        return recordDisposition;
    }

    public boolean retryable() {
        return retryable;
    }
}
