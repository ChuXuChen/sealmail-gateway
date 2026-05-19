package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class QuarantineReleaseGuard {

    private final MailProcessingTracker tracker;

    public QuarantineReleaseGuard(MailProcessingTracker tracker) {
        this.tracker = tracker;
    }

    public Message<byte[]> failIfQuarantined(Object message,
                                             MailProcessingErrorType errorType) {
        Message<byte[]> typedMessage = MailProcessingMessages.asByteMessage(message);
        MailProcessingContext context = MailProcessingMessages.context(typedMessage);
        if (context == null || !context.decision().requiresQuarantine()) {
            return typedMessage;
        }
        tracker.completeProcessing(context.processingId(), ProcessingResult.FAILED);
        throw new MailProcessingException(
                errorType,
                "Quarantine release stopped: " + releaseQuarantineDetail(context),
                context);
    }

    private String releaseQuarantineDetail(MailProcessingContext context) {
        if (context == null || context.decision().quarantine() == null) {
            return "released mail cannot be delivered";
        }
        String detail = context.decision().quarantine().detail();
        return detail != null && !detail.isBlank()
                ? detail
                : context.decision().quarantine().reason();
    }
}
