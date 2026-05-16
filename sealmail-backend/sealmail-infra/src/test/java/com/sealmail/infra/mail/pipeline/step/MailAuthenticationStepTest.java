package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.mail.auth.AuthResult;
import com.sealmail.infra.mail.auth.DmarcPolicy;
import com.sealmail.infra.mail.auth.MailAuthenticationResult;
import com.sealmail.infra.mail.auth.MailAuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailAuthenticationStepTest {

    @Test
    void authenticatesContentFilterTraffic() {
        MailAuthenticationService service = mock(MailAuthenticationService.class);
        MailAuthenticationStep step = new MailAuthenticationStep(service);
        MailEnvelope envelope = new MailEnvelope(
                "msg@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("local@example.org")),
                "127.0.0.1",
                "localhost",
                Instant.now()
        );
        byte[] payload = "From: sender@example.com\r\n\r\nbody\r\n".getBytes(StandardCharsets.ISO_8859_1);
        when(service.authenticate(payload, envelope)).thenReturn(new MailAuthenticationResult(
                AuthResult.NONE,
                AuthResult.PASS,
                AuthResult.PASS,
                DmarcPolicy.NONE,
                "sealmail; spf=none dkim=pass dmarc=pass",
                "spf=NONE, dkim=PASS, dmarc=PASS, policy=NONE"
        ));

        var result = step.execute(MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, MailProcessingContext.create(envelope))
                .build());

        assertEquals("Authentication-Results: sealmail; spf=none dkim=pass dmarc=pass\r\nFrom: sender@example.com\r\n\r\nbody\r\n",
                new String(result.getPayload(), StandardCharsets.ISO_8859_1));
        verify(service).authenticate(payload, envelope);
    }

    @Test
    void quarantinesWhenDmarcRejectFails() {
        MailAuthenticationService service = mock(MailAuthenticationService.class);
        MailAuthenticationStep step = new MailAuthenticationStep(service);
        MailEnvelope envelope = new MailEnvelope(
                "msg@example.com",
                new EmailAddress("spoof@example.com"),
                List.of(new EmailAddress("local@example.org")),
                "203.0.113.10",
                "mx.example.com",
                Instant.now()
        );
        byte[] payload = "From: spoof@example.com\r\n\r\nbody\r\n".getBytes();
        when(service.authenticate(payload, envelope)).thenReturn(new MailAuthenticationResult(
                AuthResult.FAIL,
                AuthResult.FAIL,
                AuthResult.FAIL,
                DmarcPolicy.REJECT,
                "sealmail; spf=fail dkim=fail dmarc=fail",
                "spf=FAIL, dkim=FAIL, dmarc=FAIL, policy=REJECT"
        ));

        var result = step.execute(MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, MailProcessingContext.create(envelope))
                .build());

        MailProcessingContext resultContext = context(result);
        assertTrue(resultContext.decision().requiresQuarantine());
        assertEquals("EMAIL_AUTH_FAILED", resultContext.decision().quarantine().reason());
        assertEquals("spf=FAIL, dkim=FAIL, dmarc=FAIL, policy=REJECT", resultContext.decision().quarantine().detail());
        assertEquals(new String(payload), new String(result.getPayload()));
    }

    @Test
    void passesWhenDmarcQuarantinePolicyIsDisabled() {
        MailAuthenticationService service = mock(MailAuthenticationService.class);
        MailAuthenticationStep step = new MailAuthenticationStep(service);
        MailEnvelope envelope = new MailEnvelope(
                "msg@example.com",
                new EmailAddress("spoof@example.com"),
                List.of(new EmailAddress("local@example.org")),
                "203.0.113.10",
                "mx.example.com",
                Instant.now()
        );
        byte[] payload = "From: spoof@example.com\r\n\r\nbody\r\n".getBytes();
        when(service.authenticate(payload, envelope)).thenReturn(new MailAuthenticationResult(
                AuthResult.FAIL,
                AuthResult.FAIL,
                AuthResult.FAIL,
                DmarcPolicy.REJECT,
                "sealmail; spf=fail dkim=fail dmarc=fail",
                "spf=FAIL, dkim=FAIL, dmarc=FAIL, policy=REJECT",
                false
        ));

        var result = step.execute(MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, MailProcessingContext.create(envelope))
                .build());

        assertEquals(null, context(result).decision().quarantine());
    }

    private static MailProcessingContext context(org.springframework.messaging.Message<?> message) {
        return (MailProcessingContext) message.getHeaders().get(MailProcessingHeaders.CONTEXT);
    }
}
