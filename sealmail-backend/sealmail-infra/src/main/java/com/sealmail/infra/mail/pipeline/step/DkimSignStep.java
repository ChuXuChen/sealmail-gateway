package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.mailauth.DkimSigningPort;
import com.sealmail.domain.mailauth.DomainMailAuthPolicy;
import com.sealmail.domain.mailauth.MailAuthPolicyRepository;
import com.sealmail.domain.mailauth.SigningResult;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.infra.mail.pipeline.MailProcessingMessages;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class DkimSignStep {

    private final MailAuthPolicyRepository policyRepository;
    private final DkimSigningPort signingPort;

    public DkimSignStep(MailAuthPolicyRepository policyRepository,
                        DkimSigningPort signingPort) {
        this.policyRepository = policyRepository;
        this.signingPort = signingPort;
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
            DomainMailAuthPolicy policy = policyRepository.findDomainPolicy(envelope.getSender().getDomain())
                    .orElseGet(() -> DomainMailAuthPolicy.defaults(envelope.getSender().getDomain()));
            if (!policy.enabled() || !policy.dkimSigningPolicy().enabled()) {
                return message;
            }
            SigningResult result = signingPort.sign(message.getPayload(), policy);
            if (!result.signed()) {
                throw new IllegalStateException(result.detail());
            }
            return MailProcessingMessages.withPayload(message, result.content());
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
        return MailProcessingMessages.context(message);
    }
}
