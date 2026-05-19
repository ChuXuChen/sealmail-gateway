package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class MailFlowStepRunner {

    private final MailProcessingTracker tracker;

    public MailFlowStepRunner(MailProcessingTracker tracker) {
        this.tracker = tracker;
    }

    public Message<byte[]> runTrackedStep(Object message,
                                          MailFlowStep step,
                                          MailProcessingTracker.StepAction action) {
        return runTrackedStep(message, step.stepName(), step.errorType(), action);
    }

    public Message<byte[]> runTrackedStep(Object message,
                                          String stepName,
                                          MailProcessingErrorType errorType,
                                          MailProcessingTracker.StepAction action) {
        return runFlowAction(
                message,
                errorType,
                typedMessage -> tracker.executeStep(typedMessage, stepName, errorType, action));
    }

    public Message<byte[]> runFlowAction(Object message,
                                         MailFlowStep step,
                                         MailProcessingTracker.StepAction action) {
        return runFlowAction(message, step.errorType(), action);
    }

    public Message<byte[]> runFlowAction(Object message,
                                         MailProcessingErrorType errorType,
                                         MailProcessingTracker.StepAction action) {
        Message<byte[]> typedMessage = MailProcessingMessages.asByteMessage(message);
        try {
            return action.apply(typedMessage);
        } catch (Exception e) {
            throw mailProcessingException(typedMessage, errorType, e);
        }
    }

    private MailProcessingException mailProcessingException(Message<byte[]> message,
                                                           MailProcessingErrorType errorType,
                                                           Exception error) {
        if (error instanceof MailProcessingException mailProcessingException) {
            return mailProcessingException;
        }
        return new MailProcessingException(errorType, error.getMessage(), context(message), error);
    }

    private MailProcessingContext context(Message<?> message) {
        return MailProcessingMessages.context(message);
    }
}
