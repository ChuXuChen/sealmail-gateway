package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.mailauth.AuthenticationMechanism;
import com.sealmail.domain.mailauth.AuthenticationMechanismResult;
import com.sealmail.domain.mailauth.AuthenticationResult;
import com.sealmail.domain.mailauth.AuthenticationResultSet;
import com.sealmail.domain.mailauth.AuthenticationResultsHeader;
import com.sealmail.domain.mailauth.DomainMailAuthPolicy;
import com.sealmail.domain.mailauth.DnsProbeResult;
import com.sealmail.domain.mailauth.MailAuthDecision;
import com.sealmail.domain.mailauth.MailAuthPolicy;
import com.sealmail.domain.mailauth.MailAuthPolicyRepository;
import com.sealmail.domain.mailauth.MailAuthVerifierPort;
import com.sealmail.domain.mailauth.MailSourceIdentity;
import com.sealmail.domain.mailauth.TrustedMailSourcePort;
import com.sealmail.domain.mailauth.TrustedProxyMode;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.mail.pipeline.UnifiedMailDecisionService;
import com.sealmail.infra.mailauth.AuthenticationResultsHeaderWriter;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailAuthenticationStepTest {

    @Test
    void authenticatesWithTrustedSourceAndStoresResultInContext() {
        MailAuthPolicy policy = policy();
        CapturingTrustedSource trustedSource = new CapturingTrustedSource(
                new MailSourceIdentity("203.0.113.10", "example.com", null, "helo", true, "trusted header"));
        CapturingVerifier verifier = new CapturingVerifier(passResult());
        MailAuthenticationStep step = new MailAuthenticationStep(
                repository(policy),
                trustedSource,
                verifier,
                new AuthenticationResultsHeaderWriter(),
                new UnifiedMailDecisionService(),
                null);
        MailEnvelope envelope = envelope("127.0.0.1");
        byte[] payload = payload();

        var result = step.execute(MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, MailProcessingContext.initial(
                        envelope,
                        MailDirection.INBOUND,
                        "smtp",
                        payload,
                        "subject",
                        "127.0.0.1"))
                .build());

        String content = new String(result.getPayload(), StandardCharsets.ISO_8859_1);
        MailProcessingContext resultContext = context(result);
        assertTrue(content.startsWith("Authentication-Results: sealmail; spf=pass"));
        assertEquals("127.0.0.1", trustedSource.candidate.sourceIp());
        assertEquals("203.0.113.10", verifier.sourceIdentity.sourceIp());
        assertEquals(policy, verifier.policy);
        assertEquals(AuthenticationResult.PASS, resultContext.mailAuthResults().spf().result());
        assertFalse(resultContext.decision().requiresQuarantine());
    }

    @Test
    void quarantinesWhenVerifierDecisionRequiresQuarantine() {
        MailAuthenticationStep step = new MailAuthenticationStep(
                repository(policy()),
                (rawContent, candidate, policy) -> candidate,
                (rawContent, sourceIdentity, policy) -> failResult(),
                new AuthenticationResultsHeaderWriter(),
                new UnifiedMailDecisionService(),
                null);
        byte[] payload = payload();

        var result = step.execute(MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, MailProcessingContext.create(envelope("203.0.113.10")))
                .build());

        MailProcessingContext resultContext = context(result);
        assertTrue(resultContext.decision().requiresQuarantine());
        assertEquals("EMAIL_AUTH_FAILED", resultContext.decision().quarantine().reason());
        assertEquals("DMARC reject", resultContext.decision().quarantine().detail());
        assertEquals(MailRecordDisposition.EXCEPTION, resultContext.recordDisposition());
        assertEquals(AuthenticationResult.FAIL, resultContext.mailAuthResults().dmarc().result());
    }

    private static AuthenticationResultSet passResult() {
        AuthenticationMechanismResult spf = new AuthenticationMechanismResult(
                AuthenticationMechanism.SPF,
                AuthenticationResult.PASS,
                "example.com",
                "203.0.113.10",
                "SPF pass");
        AuthenticationMechanismResult dmarc = new AuthenticationMechanismResult(
                AuthenticationMechanism.DMARC,
                AuthenticationResult.PASS,
                "example.com",
                "none",
                "DMARC pass");
        return new AuthenticationResultSet(
                spf,
                List.of(AuthenticationMechanismResult.none(AuthenticationMechanism.DKIM, "no DKIM")),
                dmarc,
                MailAuthDecision.recordOnly("DMARC pass"),
                new AuthenticationResultsHeader("sealmail", "sealmail; spf=pass dkim=none dmarc=pass"));
    }

    private static AuthenticationResultSet failResult() {
        AuthenticationMechanismResult spf = new AuthenticationMechanismResult(
                AuthenticationMechanism.SPF,
                AuthenticationResult.FAIL,
                "example.com",
                "203.0.113.10",
                "SPF fail");
        AuthenticationMechanismResult dmarc = new AuthenticationMechanismResult(
                AuthenticationMechanism.DMARC,
                AuthenticationResult.FAIL,
                "example.com",
                "reject",
                "DMARC reject");
        return new AuthenticationResultSet(
                spf,
                List.of(AuthenticationMechanismResult.none(AuthenticationMechanism.DKIM, "no DKIM")),
                dmarc,
                MailAuthDecision.applyPolicy("DMARC reject"),
                new AuthenticationResultsHeader("sealmail", "sealmail; spf=fail dkim=none dmarc=fail"));
    }

    private static MailAuthPolicy policy() {
        return new MailAuthPolicy("default", true, "sealmail", TrustedProxyMode.TRUSTED_HEADERS,
                com.sealmail.domain.mailauth.MailAuthFailureAction.APPLY_POLICY, null, null, 0);
    }

    private static MailEnvelope envelope(String remoteHost) {
        return new MailEnvelope(
                "msg@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("local@example.org")),
                remoteHost,
                "helo",
                Instant.now());
    }

    private static byte[] payload() {
        return "From: sender@example.com\r\n\r\nbody\r\n".getBytes(StandardCharsets.ISO_8859_1);
    }

    private static MailProcessingContext context(org.springframework.messaging.Message<?> message) {
        return (MailProcessingContext) message.getHeaders().get(MailProcessingHeaders.CONTEXT);
    }

    private static MailAuthPolicyRepository repository(MailAuthPolicy policy) {
        return new MailAuthPolicyRepository() {
            @Override
            public MailAuthPolicy findPolicy() {
                return policy;
            }

            @Override
            public MailAuthPolicy savePolicy(MailAuthPolicy policy) {
                return policy;
            }

            @Override
            public Optional<DomainMailAuthPolicy> findDomainPolicy(String domainName) {
                return Optional.empty();
            }

            @Override
            public DomainMailAuthPolicy saveDomainPolicy(DomainMailAuthPolicy policy) {
                return policy;
            }

            @Override
            public List<DomainMailAuthPolicy> findDomainPolicies() {
                return List.of();
            }

            @Override
            public DnsProbeResult saveDnsProbeResult(DnsProbeResult result) {
                return result;
            }

            @Override
            public List<DnsProbeResult> findLatestDnsProbeResults(String domainName) {
                return List.of();
            }
        };
    }

    private static final class CapturingTrustedSource implements TrustedMailSourcePort {
        private final MailSourceIdentity result;
        private MailSourceIdentity candidate;

        private CapturingTrustedSource(MailSourceIdentity result) {
            this.result = result;
        }

        @Override
        public MailSourceIdentity resolve(byte[] rawContent, MailSourceIdentity candidate, MailAuthPolicy policy) {
            this.candidate = candidate;
            return result;
        }
    }

    private static final class CapturingVerifier implements MailAuthVerifierPort {
        private final AuthenticationResultSet result;
        private MailSourceIdentity sourceIdentity;
        private MailAuthPolicy policy;

        private CapturingVerifier(AuthenticationResultSet result) {
            this.result = result;
        }

        @Override
        public AuthenticationResultSet verify(byte[] rawContent,
                                              MailSourceIdentity sourceIdentity,
                                              MailAuthPolicy policy) {
            this.sourceIdentity = sourceIdentity;
            this.policy = policy;
            return result;
        }
    }
}
