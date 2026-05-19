package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

public final class MailProcessingMessages {

    private MailProcessingMessages() {
    }

    public static Message<byte[]> withPayload(Message<byte[]> original, byte[] payload) {
        return MessageBuilder.withPayload(payload)
                .copyHeaders(original.getHeaders())
                .build();
    }

    public static Message<byte[]> withContext(Message<byte[]> original, MailProcessingContext context) {
        return MessageBuilder.fromMessage(original)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }

    public static Message<byte[]> withPayloadAndContext(Message<byte[]> original,
                                                        byte[] payload,
                                                        MailProcessingContext context) {
        return MessageBuilder.withPayload(payload)
                .copyHeaders(original.getHeaders())
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }

    public static Message<byte[]> asByteMessage(Object message) {
        if (message instanceof Message<?> typedMessage) {
            Object payload = typedMessage.getPayload();
            if (!(payload instanceof byte[] bytes)) {
                throw new IllegalArgumentException("Mail pipeline payload must be byte[]");
            }
            return MessageBuilder.withPayload(bytes)
                    .copyHeaders(typedMessage.getHeaders())
                    .build();
        }
        if (message instanceof byte[] bytes) {
            return MessageBuilder.withPayload(bytes).build();
        }
        throw new IllegalArgumentException("Mail pipeline message must be a Spring Message<byte[]> or byte[]");
    }

    public static Message<byte[]> quarantine(Message<byte[]> original,
                                             String reason,
                                             String detail,
                                             MailRecordDisposition disposition) {
        MailProcessingContext context = context(original);
        if (context == null) {
            throw new MailProcessingException(
                    MailProcessingErrorType.PIPELINE,
                    "Mail processing context not found in message headers",
                    null);
        }
        MailProcessingContext quarantineContext = context
                .withDecision(context.decision().withQuarantine(reason, detail))
                .withRecordDisposition(disposition != null ? disposition : MailRecordDisposition.EXCEPTION);
        return withContext(original, quarantineContext);
    }

    public static MailProcessingContext context(Message<?> message) {
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }

    public static String processingId(Message<?> message) {
        MailProcessingContext context = context(message);
        return context != null ? context.processingId() : null;
    }
}
