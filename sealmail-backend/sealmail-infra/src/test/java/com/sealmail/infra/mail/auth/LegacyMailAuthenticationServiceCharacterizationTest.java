package com.sealmail.infra.mail.auth;

import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.config.properties.MailAuthProperties;
import com.sealmail.infra.dns.DnsTxtResolver;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyMailAuthenticationServiceCharacterizationTest {

    @Test
    void disabledAuthenticationReturnsNoneResultsWithoutHeader() {
        MailAuthProperties properties = new MailAuthProperties();
        properties.setEnabled(false);
        MailAuthenticationService service = service(properties, Map.of());

        MailAuthenticationResult result = service.authenticate(message("sender@example.com"), envelope("203.0.113.10"));

        assertEquals(AuthResult.NONE, result.spf());
        assertEquals(AuthResult.NONE, result.dkim());
        assertEquals(AuthResult.NONE, result.dmarc());
        assertEquals("", result.header());
        assertFalse(result.shouldQuarantine());
    }

    @Test
    void contentFilterLoopbackRemoteHostProducesSpfNoneAndDmarcFailureCanQuarantineByPolicy() {
        MailAuthProperties properties = new MailAuthProperties();
        properties.setAuthservId("sealmail-test");
        properties.setSkipPrivateRelay(true);
        MailAuthenticationService service = service(properties, Map.of(
                "example.com", List.of("v=spf1 -all"),
                "_dmarc.example.com", List.of("v=DMARC1; p=reject")
        ));

        MailAuthenticationResult result = service.authenticate(
                message("sender@example.com"),
                envelope("127.0.0.1"));

        assertEquals(AuthResult.NONE, result.spf());
        assertEquals(AuthResult.NONE, result.dkim());
        assertEquals(AuthResult.FAIL, result.dmarc());
        assertEquals(DmarcPolicy.REJECT, result.dmarcPolicy());
        assertEquals("sealmail-test; spf=none smtp.mailfrom=example.com; dkim=none; dmarc=fail header.from=example.com",
                result.header());
        assertTrue(result.shouldQuarantine());
    }

    @Test
    void logOnlyDmarcFailureActionDoesNotQuarantine() {
        MailAuthProperties properties = new MailAuthProperties();
        properties.getDmarc().setQuarantineRejectPolicy(false);
        properties.setSkipPrivateRelay(false);
        MailAuthenticationService service = service(properties, Map.of(
                "example.com", List.of("v=spf1 -all"),
                "_dmarc.example.com", List.of("v=DMARC1; p=reject")
        ));

        MailAuthenticationResult result = service.authenticate(
                message("sender@example.com"),
                envelope("203.0.113.10"));

        assertEquals(AuthResult.FAIL, result.dmarc());
        assertFalse(result.shouldQuarantine());
    }

    private static MailAuthenticationService service(MailAuthProperties properties, Map<String, List<String>> txt) {
        DnsTxtResolver dns = new FakeDnsTxtResolver(txt);
        return new MailAuthenticationService(
                properties,
                new SpfVerifier(dns, properties),
                new DkimVerifier(dns),
                new DmarcVerifier(dns, properties));
    }

    private static byte[] message(String from) {
        return ("From: " + from + "\r\n\r\nbody\r\n").getBytes(StandardCharsets.ISO_8859_1);
    }

    private static MailEnvelope envelope(String remoteHost) {
        return new MailEnvelope(
                "legacy-mailauth@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("local@example.org")),
                remoteHost,
                "mx.example.net",
                Instant.parse("2025-04-01T12:00:00Z"));
    }

    private static final class FakeDnsTxtResolver extends DnsTxtResolver {
        private final Map<String, List<String>> txt;

        private FakeDnsTxtResolver(Map<String, List<String>> txt) {
            this.txt = txt;
        }

        @Override
        public List<String> txt(String name) {
            return txt.getOrDefault(name, List.of());
        }
    }
}
