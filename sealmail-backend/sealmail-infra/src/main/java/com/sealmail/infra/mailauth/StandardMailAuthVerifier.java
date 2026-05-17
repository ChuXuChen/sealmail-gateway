package com.sealmail.infra.mailauth;

import com.sealmail.domain.mailauth.AuthenticationMechanism;
import com.sealmail.domain.mailauth.AuthenticationMechanismResult;
import com.sealmail.domain.mailauth.AuthenticationResult;
import com.sealmail.domain.mailauth.AuthenticationResultSet;
import com.sealmail.domain.mailauth.AuthenticationResultsHeader;
import com.sealmail.domain.mailauth.MailAuthDecision;
import com.sealmail.domain.mailauth.MailAuthFailureAction;
import com.sealmail.domain.mailauth.MailAuthPolicy;
import com.sealmail.domain.mailauth.MailAuthVerifierPort;
import com.sealmail.domain.mailauth.MailSourceIdentity;
import com.sealmail.infra.config.properties.MailAuthProperties;
import com.sealmail.infra.dns.DnsTxtResolver;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StandardMailAuthVerifier implements MailAuthVerifierPort {

    private final MailAuthProperties properties;
    private final SpfEvaluator spfEvaluator;
    private final DkimRsaSha256Verifier dkimVerifier;
    private final DmarcEvaluator dmarcEvaluator;

    public StandardMailAuthVerifier(DnsTxtResolver dns, MailAuthProperties properties) {
        this.properties = properties;
        this.spfEvaluator = new SpfEvaluator(dns, properties);
        this.dkimVerifier = new DkimRsaSha256Verifier(dns);
        this.dmarcEvaluator = new DmarcEvaluator(dns, properties);
    }

    @Override
    public AuthenticationResultSet verify(byte[] rawContent,
                                          MailSourceIdentity sourceIdentity,
                                          MailAuthPolicy policy) {
        if ((policy != null && !policy.enabled()) || !properties.isEnabled()) {
            return AuthenticationResultSet.empty();
        }
        try {
            MimeMessage message = MailAuthMimeSupport.parse(rawContent);
            String fromDomain = sourceIdentity != null && sourceIdentity.headerFromDomain() != null
                    ? sourceIdentity.headerFromDomain()
                    : MailAuthMimeSupport.fromDomain(message);
            String mailFromDomain = sourceIdentity != null ? sourceIdentity.envelopeFromDomain() : null;
            String sourceIp = sourceIdentity != null ? sourceIdentity.sourceIp() : null;
            AuthenticationMechanismResult spf = spfEvaluator.verify(sourceIp, mailFromDomain);
            List<AuthenticationMechanismResult> dkim = properties.getDkim().isEnabled()
                    ? dkimVerifier.verify(rawContent)
                    : List.of(new AuthenticationMechanismResult(AuthenticationMechanism.DKIM,
                            AuthenticationResult.NONE, null, null, "DKIM disabled"));
            DmarcEvaluator.DmarcEvaluation dmarc = dmarcEvaluator.verify(fromDomain, mailFromDomain, dkim, spf);
            AuthenticationMechanismResult dmarcResult = new AuthenticationMechanismResult(
                    AuthenticationMechanism.DMARC,
                    dmarc.result(),
                    dmarc.domain(),
                    dmarc.policy().tagValue(),
                    dmarc.detail());
            AuthenticationResultsHeader header = new AuthenticationResultsHeader(
                    authservId(policy),
                    authenticationResultsHeader(authservId(policy), spf, dkim, dmarcResult, fromDomain, mailFromDomain));
            return new AuthenticationResultSet(spf, dkim, dmarcResult, decision(dmarc, policy), header);
        } catch (Exception e) {
            String authservId = authservId(policy);
            AuthenticationMechanismResult tempError = new AuthenticationMechanismResult(
                    AuthenticationMechanism.SPF,
                    AuthenticationResult.TEMPERROR,
                    null,
                    null,
                    "Mail authentication failed: " + e.getClass().getSimpleName());
            AuthenticationMechanismResult dmarc = new AuthenticationMechanismResult(
                    AuthenticationMechanism.DMARC,
                    AuthenticationResult.TEMPERROR,
                    null,
                    null,
                    tempError.detail());
            return new AuthenticationResultSet(
                    tempError,
                    List.of(new AuthenticationMechanismResult(AuthenticationMechanism.DKIM,
                            AuthenticationResult.TEMPERROR, null, null, tempError.detail())),
                    dmarc,
                    MailAuthDecision.recordOnly(tempError.detail()),
                    new AuthenticationResultsHeader(authservId,
                            authservId + "; spf=temperror dkim=temperror dmarc=temperror"));
        }
    }

    private MailAuthDecision decision(DmarcEvaluator.DmarcEvaluation dmarc, MailAuthPolicy policy) {
        MailAuthFailureAction action = policy != null ? policy.failureDefaultAction() : MailAuthFailureAction.LOG_ONLY;
        boolean policyFailure = dmarc.result() == AuthenticationResult.FAIL
                && (dmarc.policy() == com.sealmail.domain.mailauth.DmarcPolicyMode.QUARANTINE
                || dmarc.policy() == com.sealmail.domain.mailauth.DmarcPolicyMode.REJECT);
        if (action == MailAuthFailureAction.FORCE_ALLOW || !policyFailure) {
            return MailAuthDecision.recordOnly(dmarc.detail());
        }
        return new MailAuthDecision(action, "EMAIL_AUTH_FAILED", dmarc.detail());
    }

    private String authenticationResultsHeader(String authservId,
                                               AuthenticationMechanismResult spf,
                                               List<AuthenticationMechanismResult> dkim,
                                               AuthenticationMechanismResult dmarc,
                                               String fromDomain,
                                               String mailFromDomain) {
        AuthenticationMechanismResult bestDkim = dkim.stream()
                .filter(result -> result.result() == AuthenticationResult.PASS)
                .findFirst()
                .orElseGet(() -> dkim.stream().findFirst()
                        .orElse(new AuthenticationMechanismResult(AuthenticationMechanism.DKIM,
                                AuthenticationResult.NONE, null, null, null)));
        return authservId
                + "; spf=" + spf.result().token() + " smtp.mailfrom=" + value(mailFromDomain)
                + "; dkim=" + bestDkim.result().token()
                + "; dmarc=" + dmarc.result().token() + " header.from=" + value(fromDomain);
    }

    private String authservId(MailAuthPolicy policy) {
        return policy != null && policy.authservId() != null ? policy.authservId() : properties.getAuthservId();
    }

    private String value(String value) {
        return value != null ? value : "unknown";
    }
}
