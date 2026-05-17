package com.sealmail.infra.mail.auth;

import com.sealmail.infra.config.properties.MailAuthProperties;
import com.sealmail.infra.dns.DnsTxtResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyDmarcVerifierCharacterizationTest {

    @Test
    void passesWhenSpfOrDkimPassesAndAlignsInRelaxedMode() {
        DmarcVerifier verifier = new DmarcVerifier(new FakeDnsTxtResolver(Map.of(
                "_dmarc.example.com", List.of("v=DMARC1; p=quarantine; adkim=r; aspf=r")
        )), new MailAuthProperties());

        DmarcVerifier.DmarcDecision spfAligned = verifier.verify(
                "example.com",
                "mail.example.com",
                null,
                AuthResult.PASS,
                AuthResult.NONE);
        DmarcVerifier.DmarcDecision dkimAligned = verifier.verify(
                "example.com",
                null,
                "sign.example.com",
                AuthResult.NONE,
                AuthResult.PASS);

        assertEquals(AuthResult.PASS, spfAligned.result());
        assertEquals(DmarcPolicy.QUARANTINE, spfAligned.policy());
        assertEquals(AuthResult.PASS, dkimAligned.result());
        assertEquals(DmarcPolicy.QUARANTINE, dkimAligned.policy());
    }

    @Test
    void strictModeRequiresExactDomainMatch() {
        DmarcVerifier verifier = new DmarcVerifier(new FakeDnsTxtResolver(Map.of(
                "_dmarc.example.com", List.of("v=DMARC1; p=reject; adkim=s; aspf=s")
        )), new MailAuthProperties());

        DmarcVerifier.DmarcDecision subdomain = verifier.verify(
                "example.com",
                "mail.example.com",
                "sign.example.com",
                AuthResult.PASS,
                AuthResult.PASS);
        DmarcVerifier.DmarcDecision exact = verifier.verify(
                "example.com",
                "example.com",
                null,
                AuthResult.PASS,
                AuthResult.NONE);

        assertEquals(AuthResult.FAIL, subdomain.result());
        assertEquals(DmarcPolicy.REJECT, subdomain.policy());
        assertEquals(AuthResult.PASS, exact.result());
    }

    @Test
    void missingDmarcRecordReturnsNonePolicyNone() {
        DmarcVerifier verifier = new DmarcVerifier(new FakeDnsTxtResolver(Map.of()), new MailAuthProperties());

        DmarcVerifier.DmarcDecision decision = verifier.verify(
                "example.com",
                "example.com",
                null,
                AuthResult.PASS,
                AuthResult.NONE);

        assertEquals(AuthResult.NONE, decision.result());
        assertEquals(DmarcPolicy.NONE, decision.policy());
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
