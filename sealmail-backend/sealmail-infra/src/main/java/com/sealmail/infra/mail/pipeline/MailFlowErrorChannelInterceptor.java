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

    public MailFlowErrorChannelInterceptor(@Qualifier("errorChannel") MessageChannel errorChannel) {
        this.errorChannel = errorChannel;
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        if (ex != null) {
            errorChannel.send(new ErrorMessage(errorPayload(ex), message));
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
