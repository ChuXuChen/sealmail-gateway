package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.infra.mail.auth.DkimSigner;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.mail.pipeline.MailProcessingMessages;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class DkimSignStep {

    private final DkimSigner dkimSigner;

    public DkimSignStep(DkimSigner dkimSigner) {
        this.dkimSigner = dkimSigner;
    }

    public Message<byte[]> execute(Message<byte[]> message) {
        MailProcessingContext context = context(message);
        if (context == null || !context.decision().dkimSigningRequired()) {
            return message;
        }
        MailEnvelope envelope = context.envelope();
        if (envelope == null) {
            return message;
        }
        try {
            return MailProcessingMessages.withPayload(
                    message,
                    dkimSigner.sign(message.getPayload(), envelope.getSender().getDomain()));
        } catch (Exception e) {
            throw new MailProcessingException(
                    MailProcessingErrorType.DKIM_SIGNING,
                    "DKIM signing failed: " + e.getMessage(),
                    context,
                    e);
        }
    }

    public String getStepName() {
        return "dkim-sign";
    }

    private MailProcessingContext context(Message<?> message) {
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }
}
