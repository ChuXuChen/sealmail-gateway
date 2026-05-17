package com.sealmail.infra.mail.auth;

import com.sealmail.infra.config.properties.MailAuthProperties;
import com.sealmail.infra.dns.DnsTxtResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacySpfVerifierCharacterizationTest {

    @Test
    void skipsPrivateRelayWhenConfigured() {
        MailAuthProperties properties = new MailAuthProperties();
        properties.setSkipPrivateRelay(true);
        SpfVerifier verifier = new SpfVerifier(new FakeDnsTxtResolver(Map.of(
                "example.com", List.of("v=spf1 -all")
        ), Map.of(), Map.of()), properties);

        assertEquals(AuthResult.NONE, verifier.verify("127.0.0.1", "example.com"));
    }

    @Test
    void evaluatesIp4AIncludeMxRedirectAndAllWithCurrentExactAddressMatching() {
        MailAuthProperties properties = new MailAuthProperties();
        properties.setSkipPrivateRelay(false);
        SpfVerifier verifier = new SpfVerifier(new FakeDnsTxtResolver(
                Map.of(
                        "example.com", List.of("v=spf1 include:sender.example.net -all"),
                        "sender.example.net", List.of("v=spf1 ip4:203.0.113.10 -all"),
                        "host.example.com", List.of("v=spf1 a -all"),
                        "mx.example.com", List.of("v=spf1 mx -all"),
                        "redirect.example.com", List.of("v=spf1 redirect=sender.example.net"),
                        "soft.example.com", List.of("v=spf1 ~all")
                ),
                Map.of("host.example.com", List.of("203.0.113.11"),
                        "mx1.example.com", List.of("203.0.113.12")),
                Map.of("mx.example.com", List.of("10 mx1.example.com."))
        ), properties);

        assertEquals(AuthResult.PASS, verifier.verify("203.0.113.10", "example.com"));
        assertEquals(AuthResult.PASS, verifier.verify("203.0.113.11", "host.example.com"));
        assertEquals(AuthResult.PASS, verifier.verify("203.0.113.12", "mx.example.com"));
        assertEquals(AuthResult.PASS, verifier.verify("203.0.113.10", "redirect.example.com"));
        assertEquals(AuthResult.SOFTFAIL, verifier.verify("203.0.113.99", "soft.example.com"));
        assertEquals(AuthResult.FAIL, verifier.verify("203.0.113.99", "example.com"));
    }

    @Test
    void currentIp4CidrMechanismFallsThroughToAllPolicy() {
        MailAuthProperties properties = new MailAuthProperties();
        properties.setSkipPrivateRelay(false);
        SpfVerifier verifier = new SpfVerifier(new FakeDnsTxtResolver(Map.of(
                "example.com", List.of("v=spf1 ip4:203.0.113.0/24 -all")
        ), Map.of(), Map.of()), properties);

        assertEquals(AuthResult.FAIL, verifier.verify("203.0.113.10", "example.com"));
    }

    @Test
    void maxDnsLookupDepthOverflowIsPermerror() {
        MailAuthProperties properties = new MailAuthProperties();
        properties.setSkipPrivateRelay(false);
        properties.getSpf().setMaxDnsLookups(0);
        SpfVerifier verifier = new SpfVerifier(new FakeDnsTxtResolver(Map.of(
                "example.com", List.of("v=spf1 include:sender.example.net -all"),
                "sender.example.net", List.of("v=spf1 ip4:203.0.113.10 -all")
        ), Map.of(), Map.of()), properties);

        assertEquals(AuthResult.PERMERROR, verifier.verify("203.0.113.10", "example.com"));
    }

    private static final class FakeDnsTxtResolver extends DnsTxtResolver {
        private final Map<String, List<String>> txt;
        private final Map<String, List<String>> a;
        private final Map<String, List<String>> mx;

        private FakeDnsTxtResolver(Map<String, List<String>> txt,
                                   Map<String, List<String>> a,
                                   Map<String, List<String>> mx) {
            this.txt = txt;
            this.a = a;
            this.mx = mx;
        }

        @Override
        public List<String> txt(String name) {
            return txt.getOrDefault(name, List.of());
        }

        @Override
        public List<String> a(String name) {
            return a.getOrDefault(name, List.of());
        }

        @Override
        public List<String> mx(String name) {
            return mx.getOrDefault(name, List.of());
        }
    }
}
