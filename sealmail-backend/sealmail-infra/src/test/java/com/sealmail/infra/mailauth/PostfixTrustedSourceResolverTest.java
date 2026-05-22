package com.sealmail.infra.mailauth;

import com.sealmail.domain.mailauth.MailAuthFailureAction;
import com.sealmail.domain.mailauth.MailAuthPolicy;
import com.sealmail.domain.mailauth.MailSourceIdentity;
import com.sealmail.domain.mailauth.TrustedProxyMode;
import com.sealmail.infra.config.properties.MailAuthProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostfixTrustedSourceResolverTest {

    @Test
    void disabledModeKeepsPeerAddressEvenWhenHeaderExists() {
        PostfixTrustedSourceResolver resolver = new PostfixTrustedSourceResolver(new MailAuthProperties());

        MailSourceIdentity result = resolver.resolve(
                message("203.0.113.10"),
                candidate("127.0.0.1"),
                policy(TrustedProxyMode.DISABLED));

        assertEquals("127.0.0.1", result.sourceIp());
        assertFalse(result.trustedProxyOverride());
    }

    @Test
    void trustedHeadersModeRequiresTrustedRelay() {
        PostfixTrustedSourceResolver resolver = new PostfixTrustedSourceResolver(new MailAuthProperties());

        MailSourceIdentity result = resolver.resolve(
                message("203.0.113.10"),
                candidate("198.51.100.20"),
                policy(TrustedProxyMode.TRUSTED_HEADERS));

        assertEquals("198.51.100.20", result.sourceIp());
        assertFalse(result.trustedProxyOverride());
    }

    @Test
    void trustedHeadersModeAcceptsOriginalIpFromTrustedRelay() {
        PostfixTrustedSourceResolver resolver = new PostfixTrustedSourceResolver(new MailAuthProperties());

        MailSourceIdentity result = resolver.resolve(
                message("203.0.113.10"),
                candidate("127.0.0.1"),
                policy(TrustedProxyMode.TRUSTED_HEADERS));

        assertEquals("203.0.113.10", result.sourceIp());
        assertTrue(result.trustedProxyOverride());
    }

    @Test
    void trustedHeadersModeFallsBackToPublicIpFromPostfixReceivedHeader() {
        MailAuthProperties properties = new MailAuthProperties();
        properties.getTrustedSource().setTrustedRelayCidrs(List.of("172.16.0.0/12"));
        PostfixTrustedSourceResolver resolver = new PostfixTrustedSourceResolver(properties);

        MailSourceIdentity result = resolver.resolve(
                receivedMessage("8.217.136.171"),
                candidate("172.18.0.8"),
                policy(TrustedProxyMode.TRUSTED_HEADERS));

        assertEquals("8.217.136.171", result.sourceIp());
        assertTrue(result.trustedProxyOverride());
    }

    private static byte[] message(String originalIp) {
        return ("X-Original-Client-IP: " + originalIp + "\r\n"
                + "From: sender@example.com\r\n"
                + "\r\n"
                + "body\r\n").getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] receivedMessage(String originalIp) {
        return ("Received: from relay.internal (sealmail-gateway-beta-postfix-1 [172.18.0.8])\r\n"
                + "\tby backend.internal with SMTP; Fri, 22 May 2026 03:44:13 +0000\r\n"
                + "Received: from mx-alpha.example (unknown [" + originalIp + "])\r\n"
                + "\tby mx-beta.example with ESMTP; Fri, 22 May 2026 03:44:13 +0000\r\n"
                + "From: sender@example.com\r\n"
                + "\r\n"
                + "body\r\n").getBytes(StandardCharsets.ISO_8859_1);
    }

    private static MailSourceIdentity candidate(String sourceIp) {
        return new MailSourceIdentity(sourceIp, "example.com", "example.com", "mx", false, null);
    }

    private static MailAuthPolicy policy(TrustedProxyMode mode) {
        return new MailAuthPolicy("default", true, "sealmail", mode,
                MailAuthFailureAction.LOG_ONLY, null, null, 0);
    }
}
