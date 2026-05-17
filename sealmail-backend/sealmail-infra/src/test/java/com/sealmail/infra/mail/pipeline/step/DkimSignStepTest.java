package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.mailauth.DkimKeyRef;
import com.sealmail.domain.mailauth.DkimSelector;
import com.sealmail.domain.mailauth.DkimSigningPolicy;
import com.sealmail.domain.mailauth.DkimSigningPort;
import com.sealmail.domain.mailauth.DmarcPublicationPolicy;
import com.sealmail.domain.mailauth.DomainMailAuthPolicy;
import com.sealmail.domain.mailauth.DnsProbeResult;
import com.sealmail.domain.mailauth.MailAuthPolicy;
import com.sealmail.domain.mailauth.MailAuthPolicyRepository;
import com.sealmail.domain.mailauth.SigningResult;
import com.sealmail.domain.mailauth.SpfPublicationPolicy;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DkimSignStepTest {

    @Test
    void signsWhenContextRequiresDkimAndDomainPolicyIsReady() {
        byte[] signedContent = "DKIM-Signature: x\r\nFrom: sender@example.com\r\n\r\nbody\r\n"
                .getBytes(StandardCharsets.ISO_8859_1);
        CapturingSigningPort signingPort = new CapturingSigningPort(new SigningResult(
                signedContent,
                true,
                "example.com",
                "sealmail",
                "signed"));
        DkimSignStep step = new DkimSignStep(repository(Optional.of(enabledPolicy())), signingPort);
        byte[] payload = payload();

        var result = step.execute(MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context(true))
                .build());

        assertArrayEquals(signedContent, result.getPayload());
        assertArrayEquals(payload, signingPort.rawContent);
        assertEquals("example.com", signingPort.policy.domainName());
    }

    @Test
    void skipsWhenDkimIsNotRequiredByContext() {
        CapturingSigningPort signingPort = new CapturingSigningPort(new SigningResult(
                "signed".getBytes(StandardCharsets.ISO_8859_1),
                true,
                "example.com",
                "sealmail",
                "signed"));
        DkimSignStep step = new DkimSignStep(repository(Optional.of(enabledPolicy())), signingPort);
        byte[] payload = payload();

        var result = step.execute(MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context(false))
                .build());

        assertArrayEquals(payload, result.getPayload());
        assertFalse(signingPort.called);
    }

    @Test
    void skipsWhenDomainPolicyIsMissingOrDisabled() {
        CapturingSigningPort signingPort = new CapturingSigningPort(new SigningResult(
                "signed".getBytes(StandardCharsets.ISO_8859_1),
                true,
                "example.com",
                "sealmail",
                "signed"));
        DkimSignStep step = new DkimSignStep(repository(Optional.empty()), signingPort);
        byte[] payload = payload();

        var result = step.execute(MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context(true))
                .build());

        assertArrayEquals(payload, result.getPayload());
        assertFalse(signingPort.called);
    }

    @Test
    void wrapsSigningFailureAsMailProcessingException() {
        CapturingSigningPort signingPort = new CapturingSigningPort(new SigningResult(
                payload(),
                false,
                "example.com",
                "sealmail",
                "private key missing"));
        DkimSignStep step = new DkimSignStep(repository(Optional.of(enabledPolicy())), signingPort);

        MailProcessingException error = assertThrows(MailProcessingException.class, () ->
                step.execute(MessageBuilder.withPayload(payload())
                        .setHeader(MailProcessingHeaders.CONTEXT, context(true))
                        .build()));

        assertEquals(MailProcessingErrorType.DKIM_SIGNING, error.errorType());
        assertTrue(error.getMessage().contains("private key missing"));
    }

    private static MailProcessingContext context(boolean dkimRequired) {
        MailProcessingContext context = MailProcessingContext.create(envelope());
        if (dkimRequired) {
            return context.withDecision(context.decision().withDkimSigningRequired(true));
        }
        return context;
    }

    private static MailEnvelope envelope() {
        return new MailEnvelope(
                "msg@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.org")),
                "127.0.0.1",
                "helo",
                Instant.now());
    }

    private static byte[] payload() {
        return "From: sender@example.com\r\n\r\nbody\r\n".getBytes(StandardCharsets.ISO_8859_1);
    }

    private static DomainMailAuthPolicy enabledPolicy() {
        return new DomainMailAuthPolicy(
                "example.com",
                true,
                new DkimSigningPolicy(
                        true,
                        new DkimSelector("sealmail"),
                        new DkimKeyRef("DKIM_PRIVATE_KEY", null),
                        List.of("from", "to", "subject", "date", "message-id")),
                SpfPublicationPolicy.disabled(),
                DmarcPublicationPolicy.disabled(),
                null,
                null,
                0);
    }

    private static MailAuthPolicyRepository repository(Optional<DomainMailAuthPolicy> policy) {
        return new MailAuthPolicyRepository() {
            @Override
            public MailAuthPolicy findPolicy() {
                return MailAuthPolicy.defaults();
            }

            @Override
            public MailAuthPolicy savePolicy(MailAuthPolicy policy) {
                return policy;
            }

            @Override
            public Optional<DomainMailAuthPolicy> findDomainPolicy(String domainName) {
                return policy;
            }

            @Override
            public DomainMailAuthPolicy saveDomainPolicy(DomainMailAuthPolicy policy) {
                return policy;
            }

            @Override
            public List<DomainMailAuthPolicy> findDomainPolicies() {
                return policy.stream().toList();
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

    private static final class CapturingSigningPort implements DkimSigningPort {
        private final SigningResult result;
        private byte[] rawContent;
        private DomainMailAuthPolicy policy;
        private boolean called;

        private CapturingSigningPort(SigningResult result) {
            this.result = result;
        }

        @Override
        public SigningResult sign(byte[] rawContent, DomainMailAuthPolicy policy) {
            this.called = true;
            this.rawContent = rawContent;
            this.policy = policy;
            return result;
        }
    }
}
