package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailProcessingException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.stereotype.Component;

@Component
public class MailFlowErrorChannelInterceptor implements ChannelInterceptor {

    private final MessageChannel errorChannel;
    private final MailFlowErrorHandlingState errorHandlingState;

    public MailFlowErrorChannelInterceptor(@Qualifier("errorChannel") MessageChannel errorChannel,
                                           MailFlowErrorHandlingState errorHandlingState) {
        this.errorChannel = errorChannel;
        this.errorHandlingState = errorHandlingState;
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        if (ex != null) {
            errorHandlingState.clear();
            try {
                boolean handled = errorChannel.send(new ErrorMessage(errorPayload(ex), message));
                if (!handled) {
                    errorHandlingState.markFailed(new IllegalStateException("Error channel rejected message"));
                }
            } catch (RuntimeException e) {
                errorHandlingState.markFailed(e);
                throw e;
            }
        }
    }

    private Throwable errorPayload(Exception ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof MailProcessingException) {
                return current;
            }
            current = current.getCause();
        }
        return ex;
    }
}
