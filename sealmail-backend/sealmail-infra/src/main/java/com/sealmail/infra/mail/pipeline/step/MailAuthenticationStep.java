package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.mailauth.AuthenticationResultSet;
import com.sealmail.domain.mailauth.MailAuthDecision;
import com.sealmail.domain.mailauth.MailAuthPolicy;
import com.sealmail.domain.mailauth.MailAuthPolicyRepository;
import com.sealmail.domain.mailauth.MailAuthVerifierPort;
import com.sealmail.domain.mailauth.MailSourceIdentity;
import com.sealmail.domain.mailauth.TrustedMailSourcePort;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.infra.mail.pipeline.MailProcessingMessages;
import com.sealmail.infra.mailauth.AuthenticationResultsHeaderWriter;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class MailAuthenticationStep {

    private final MailAuthPolicyRepository policyRepository;
    private final TrustedMailSourcePort trustedSourceResolver;
    private final MailAuthVerifierPort verifier;
    private final AuthenticationResultsHeaderWriter headerWriter;

    public MailAuthenticationStep(MailAuthPolicyRepository policyRepository,
                                  TrustedMailSourcePort trustedSourceResolver,
                                  MailAuthVerifierPort verifier,
                                  AuthenticationResultsHeaderWriter headerWriter) {
        this.policyRepository = policyRepository;
        this.trustedSourceResolver = trustedSourceResolver;
        this.verifier = verifier;
        this.headerWriter = headerWriter;
    }

    public Message<byte[]> execute(Message<byte[]> message) {
        MailProcessingContext context = context(message);
        MailEnvelope envelope = context != null ? context.envelope() : null;
        AuthenticationResultSet result = authenticate(message, envelope, context);
        MailProcessingContext updatedContext = applyResult(context, result);
        byte[] payload = headerWriter.prepend(message.getPayload(), result.authenticationResultsHeader());
        Message<byte[]> resultMessage = MailProcessingMessages.withPayloadAndContext(message, payload, updatedContext);
        if (result.decision().requiresQuarantine()) {
            return MailProcessingMessages.quarantine(
                    resultMessage,
                    result.decision().reason(),
                    result.decision().detail(),
                    MailRecordDisposition.EXCEPTION);
        }
        return resultMessage;
    }

    public String getStepName() {
        return "mail-auth";
    }

    private AuthenticationResultSet authenticate(Message<byte[]> message,
                                                 MailEnvelope envelope,
                                                 MailProcessingContext context) {
        try {
            MailAuthPolicy policy = policyRepository.findPolicy();
            MailSourceIdentity candidate = candidateIdentity(message.getPayload(), envelope, context);
            MailSourceIdentity resolved = trustedSourceResolver.resolve(message.getPayload(), candidate, policy);
            return verifier.verify(message.getPayload(), resolved, policy);
        } catch (Exception e) {
            throw new MailProcessingException(
                    MailProcessingErrorType.AUTHENTICATION,
                    "Mail authentication failed: " + e.getMessage(),
                    context,
                    e);
        }
    }

    private MailProcessingContext applyResult(MailProcessingContext context, AuthenticationResultSet result) {
        if (context == null) {
            throw new MailProcessingException(
                    MailProcessingErrorType.PIPELINE,
                    "Mail processing context not found in message headers",
                    null);
        }
        MailProcessingContext updated = context.withMailAuthResults(result);
        MailAuthDecision decision = result.decision();
        if (decision.requiresQuarantine()) {
            updated = updated.withDecision(context.decision().withQuarantine(decision.reason(), decision.detail()));
        }
        return updated;
    }

    private MailSourceIdentity candidateIdentity(byte[] rawContent,
                                                 MailEnvelope envelope,
                                                 MailProcessingContext context) {
        if (envelope == null) {
            return new MailSourceIdentity(null, null, null, null, false, "mail envelope missing");
        }
        return new MailSourceIdentity(
                sourceIp(envelope, context),
                envelope.getSender().getDomain(),
                null,
                envelope.getHelo(),
                false,
                "source IP from SMTP peer");
    }

    private String sourceIp(MailEnvelope envelope, MailProcessingContext context) {
        if (context != null && context.auditTrace() != null
                && context.auditTrace().remoteAddress() != null
                && !context.auditTrace().remoteAddress().isBlank()) {
            return context.auditTrace().remoteAddress();
        }
        return envelope != null ? envelope.getRemoteHost() : null;
    }

    private MailProcessingContext context(Message<?> message) {
        return MailProcessingMessages.context(message);
    }
}
