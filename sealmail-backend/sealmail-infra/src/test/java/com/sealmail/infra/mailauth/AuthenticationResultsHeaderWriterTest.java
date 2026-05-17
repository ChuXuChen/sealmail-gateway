package com.sealmail.infra.mailauth;

import com.sealmail.domain.mailauth.AuthenticationResultsHeader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthenticationResultsHeaderWriterTest {

    @Test
    void prependsAuthenticationResultsHeader() {
        AuthenticationResultsHeaderWriter writer = new AuthenticationResultsHeaderWriter();
        byte[] content = "From: sender@example.com\r\n\r\nbody\r\n".getBytes(StandardCharsets.ISO_8859_1);

        byte[] result = writer.prepend(content, new AuthenticationResultsHeader(
                "sealmail",
                "sealmail; spf=pass smtp.mailfrom=example.com; dkim=none; dmarc=pass header.from=example.com"));

        assertEquals("""
                Authentication-Results: sealmail; spf=pass smtp.mailfrom=example.com; dkim=none; dmarc=pass header.from=example.com\r
                From: sender@example.com\r
                \r
                body\r
                """, new String(result, StandardCharsets.ISO_8859_1));
    }

    @Test
    void removesExistingHeaderForSameAuthservId() {
        AuthenticationResultsHeaderWriter writer = new AuthenticationResultsHeaderWriter();
        byte[] content = """
                Authentication-Results: sealmail; spf=fail smtp.mailfrom=bad.example\r
                Authentication-Results: upstream.example; spf=pass smtp.mailfrom=example.com\r
                From: sender@example.com\r
                \r
                body\r
                """.getBytes(StandardCharsets.ISO_8859_1);

        byte[] result = writer.prepend(content, new AuthenticationResultsHeader(
                "sealmail",
                "sealmail; spf=pass smtp.mailfrom=example.com; dkim=none; dmarc=pass header.from=example.com"));

        assertEquals("""
                Authentication-Results: sealmail; spf=pass smtp.mailfrom=example.com; dkim=none; dmarc=pass header.from=example.com\r
                Authentication-Results: upstream.example; spf=pass smtp.mailfrom=example.com\r
                From: sender@example.com\r
                \r
                body\r
                """, new String(result, StandardCharsets.ISO_8859_1));
    }

    @Test
    void removesFoldedExistingHeaderForSameAuthservId() {
        AuthenticationResultsHeaderWriter writer = new AuthenticationResultsHeaderWriter();
        byte[] content = """
                Authentication-Results: SealMail;\r
                 spf=fail smtp.mailfrom=bad.example;\r
                 dmarc=fail header.from=bad.example\r
                From: sender@example.com\r
                \r
                body\r
                """.getBytes(StandardCharsets.ISO_8859_1);

        byte[] result = writer.prepend(content, new AuthenticationResultsHeader(
                "sealmail",
                "sealmail; spf=pass smtp.mailfrom=example.com; dkim=none; dmarc=pass header.from=example.com"));

        assertEquals("""
                Authentication-Results: sealmail; spf=pass smtp.mailfrom=example.com; dkim=none; dmarc=pass header.from=example.com\r
                From: sender@example.com\r
                \r
                body\r
                """, new String(result, StandardCharsets.ISO_8859_1));
    }
}
