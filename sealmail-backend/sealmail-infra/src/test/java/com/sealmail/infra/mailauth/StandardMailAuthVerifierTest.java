package com.sealmail.infra.mailauth;

import com.sealmail.domain.mailauth.AuthenticationResult;
import com.sealmail.domain.mailauth.MailAuthFailureAction;
import com.sealmail.domain.mailauth.MailAuthPolicy;
import com.sealmail.domain.mailauth.MailSourceIdentity;
import com.sealmail.domain.mailauth.TrustedProxyMode;
import com.sealmail.infra.config.properties.MailAuthProperties;
import com.sealmail.infra.dns.DnsTxtResolver;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardMailAuthVerifierTest {

    @Test
    void verifiesSpfCidrAndDmarcAlignment() {
        MailAuthProperties properties = properties();
        StandardMailAuthVerifier verifier = new StandardMailAuthVerifier(new FakeDns(Map.of(
                "example.com", List.of("v=spf1 ip4:203.0.113.0/24 -all"),
                "bounce.example.net", List.of("v=spf1 -all"),
                "_dmarc.example.com", List.of("v=DMARC1; p=reject; aspf=s; adkim=s")
        )), properties);

        var result = verifier.verify(
                message("sender@example.com"),
                new MailSourceIdentity("203.0.113.10", "example.com", "example.com", "mx", false, null),
                policy(MailAuthFailureAction.APPLY_POLICY));

        assertEquals(AuthenticationResult.PASS, result.spf().result());
        assertEquals(AuthenticationResult.PASS, result.dmarc().result());
        assertFalse(result.decision().requiresQuarantine());
        assertTrue(result.authenticationResultsHeader().value().contains("spf=pass"));
        assertTrue(result.authenticationResultsHeader().value().contains("dmarc=pass"));
    }

    @Test
    void appliesConfiguredFailureActionWhenDmarcRejectFails() {
        MailAuthProperties properties = properties();
        StandardMailAuthVerifier verifier = new StandardMailAuthVerifier(new FakeDns(Map.of(
                "example.com", List.of("v=spf1 ip4:203.0.113.0/24 -all"),
                "bounce.example.net", List.of("v=spf1 -all"),
                "_dmarc.example.com", List.of("v=DMARC1; p=reject; aspf=s; adkim=s")
        )), properties);

        var result = verifier.verify(
                message("sender@example.com"),
                new MailSourceIdentity("198.51.100.10", "bounce.example.net", "example.com", "mx", false, null),
                policy(MailAuthFailureAction.APPLY_POLICY));

        assertEquals(AuthenticationResult.FAIL, result.spf().result());
        assertEquals(AuthenticationResult.FAIL, result.dmarc().result());
        assertTrue(result.decision().requiresQuarantine());
        assertEquals("EMAIL_AUTH_FAILED", result.decision().reason());
    }

    @Test
    void recordOnlyPolicyDoesNotQuarantineDmarcFailure() {
        MailAuthProperties properties = properties();
        StandardMailAuthVerifier verifier = new StandardMailAuthVerifier(new FakeDns(Map.of(
                "example.com", List.of("v=spf1 -all"),
                "_dmarc.example.com", List.of("v=DMARC1; p=quarantine")
        )), properties);

        var result = verifier.verify(
                message("sender@example.com"),
                new MailSourceIdentity("198.51.100.10", "example.net", "example.com", "mx", false, null),
                policy(MailAuthFailureAction.LOG_ONLY));

        assertEquals(AuthenticationResult.FAIL, result.dmarc().result());
        assertFalse(result.decision().requiresQuarantine());
    }

    @Test
    void dmarcRelaxedAlignmentUsesPublicSuffixListForIcannSuffixes() {
        MailAuthProperties properties = properties();
        StandardMailAuthVerifier verifier = new StandardMailAuthVerifier(new FakeDns(Map.of(
                "bounce.attacker.co.uk", List.of("v=spf1 ip4:203.0.113.0/24 -all"),
                "_dmarc.example.co.uk", List.of("v=DMARC1; p=reject; aspf=r; adkim=r")
        )), properties);

        var result = verifier.verify(
                message("sender@mail.customer.example.co.uk"),
                new MailSourceIdentity("203.0.113.10", "bounce.attacker.co.uk",
                        "mail.customer.example.co.uk", "mx", false, null),
                policy(MailAuthFailureAction.APPLY_POLICY));

        assertEquals(AuthenticationResult.PASS, result.spf().result());
        assertEquals(AuthenticationResult.FAIL, result.dmarc().result());
        assertTrue(result.decision().requiresQuarantine());
    }

    @Test
    void dmarcRelaxedAlignmentUsesPublicSuffixListForPrivateSuffixes() {
        MailAuthProperties properties = properties();
        StandardMailAuthVerifier verifier = new StandardMailAuthVerifier(new FakeDns(Map.of(
                "bounce.other.github.io", List.of("v=spf1 ip4:203.0.113.0/24 -all"),
                "_dmarc.tenant.github.io", List.of("v=DMARC1; p=reject; aspf=r; adkim=r")
        )), properties);

        var result = verifier.verify(
                message("sender@mail.tenant.github.io"),
                new MailSourceIdentity("203.0.113.10", "bounce.other.github.io",
                        "mail.tenant.github.io", "mx", false, null),
                policy(MailAuthFailureAction.APPLY_POLICY));

        assertEquals(AuthenticationResult.PASS, result.spf().result());
        assertEquals(AuthenticationResult.FAIL, result.dmarc().result());
        assertTrue(result.decision().requiresQuarantine());
    }

    @Test
    void dmarcOrganizationalFallbackUsesSubdomainPolicy() {
        MailAuthProperties properties = properties();
        StandardMailAuthVerifier verifier = new StandardMailAuthVerifier(new FakeDns(Map.of(
                "bounce.example.net", List.of("v=spf1 -all"),
                "_dmarc.example.com", List.of("v=DMARC1; p=none; sp=reject")
        )), properties);

        var result = verifier.verify(
                message("sender@news.example.com"),
                new MailSourceIdentity("198.51.100.10", "bounce.example.net",
                        "news.example.com", "mx", false, null),
                policy(MailAuthFailureAction.APPLY_POLICY));

        assertEquals(AuthenticationResult.FAIL, result.dmarc().result());
        assertTrue(result.decision().requiresQuarantine());
    }

    private static MailAuthProperties properties() {
        MailAuthProperties properties = new MailAuthProperties();
        properties.setSkipPrivateRelay(false);
        return properties;
    }

    private static MailAuthPolicy policy(MailAuthFailureAction action) {
        return new MailAuthPolicy("default", true, "sealmail", TrustedProxyMode.DISABLED, action, null, null, 0);
    }

    private static byte[] message(String from) {
        return ("From: " + from + "\r\n"
                + "To: local@example.org\r\n"
                + "Subject: test\r\n"
                + "Date: Sun, 17 May 2026 00:00:00 +0000\r\n"
                + "Message-ID: <test@example.com>\r\n"
                + "\r\n"
                + "body\r\n").getBytes(StandardCharsets.ISO_8859_1);
    }

    private static final class FakeDns extends DnsTxtResolver {
        private final Map<String, List<String>> txt;

        private FakeDns(Map<String, List<String>> txt) {
            this.txt = txt;
        }

        @Override
        public List<String> txt(String name) {
            return txt.getOrDefault(name, List.of());
        }
    }
}
