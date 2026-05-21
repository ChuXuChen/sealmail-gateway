package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.mailauth.AuthenticationResultSet;
import com.sealmail.domain.mailauth.MailAuthPolicy;
import com.sealmail.domain.mailauth.MailAuthPolicyRepository;
import com.sealmail.domain.mailauth.MailAuthVerifierPort;
import com.sealmail.domain.mailauth.MailSourceIdentity;
import com.sealmail.domain.mailauth.TrustedMailSourcePort;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.infra.mail.pipeline.MailProcessingMessages;
import com.sealmail.infra.mail.pipeline.MailProcessingStatusService;
import com.sealmail.infra.mail.pipeline.UnifiedMailDecisionService;
import com.sealmail.infra.mailauth.AuthenticationResultsHeaderWriter;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class MailAuthenticationStep {

    private final MailAuthPolicyRepository policyRepository;
    private final TrustedMailSourcePort trustedSourceResolver;
    private final MailAuthVerifierPort verifier;
    private final AuthenticationResultsHeaderWriter headerWriter;
    private final UnifiedMailDecisionService decisionService;
    private final MailProcessingStatusService statusService;

    public MailAuthenticationStep(MailAuthPolicyRepository policyRepository,
                                  TrustedMailSourcePort trustedSourceResolver,
                                  MailAuthVerifierPort verifier,
                                  AuthenticationResultsHeaderWriter headerWriter,
                                  UnifiedMailDecisionService decisionService,
                                  MailProcessingStatusService statusService) {
        this.policyRepository = policyRepository;
        this.trustedSourceResolver = trustedSourceResolver;
        this.verifier = verifier;
        this.headerWriter = headerWriter;
        this.decisionService = decisionService;
        this.statusService = statusService;
    }

    public Message<byte[]> execute(Message<byte[]> message) {
        MailProcessingContext context = context(message);
        MailEnvelope envelope = context != null ? context.envelope() : null;
        AuthenticationResultSet result = authenticate(message, envelope, context);
        MailProcessingContext updatedContext = decisionService.applyMailAuthentication(context, result);
        recordStatus(updatedContext, result);
        byte[] payload = headerWriter.prepend(message.getPayload(), result.authenticationResultsHeader());
        return MailProcessingMessages.withPayloadAndContext(message, payload, updatedContext);
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
            recordAuthenticationFailure(context, e.getMessage());
            throw new MailProcessingException(
                    MailProcessingErrorType.AUTHENTICATION,
                    "Mail authentication failed: " + e.getMessage(),
                    context,
                    e);
        }
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

    private void recordStatus(MailProcessingContext context, AuthenticationResultSet result) {
        if (statusService != null) {
            statusService.recordMailAuthentication(context, result);
        }
    }

    private void recordAuthenticationFailure(MailProcessingContext context, String detail) {
        if (statusService != null) {
            statusService.recordMailAuthenticationFailure(context, detail);
        }
    }
}
