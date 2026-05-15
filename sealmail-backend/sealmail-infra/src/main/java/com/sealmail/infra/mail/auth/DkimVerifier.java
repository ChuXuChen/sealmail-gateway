package com.sealmail.infra.mail.auth;

import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DkimVerifier {

    private final DnsTxtResolver dns;

    public DkimVerifier(DnsTxtResolver dns) {
        this.dns = dns;
    }

    public AuthResult verify(byte[] content) {
        try {
            MimeMessage message = MimeMessageSupport.parse(content);
            String[] dkimHeaders = message.getHeader("DKIM-Signature");
            if (dkimHeaders == null || dkimHeaders.length == 0) {
                return AuthResult.NONE;
            }
            for (String header : dkimHeaders) {
                AuthResult result = verifySignature(content, message, header);
                if (result == AuthResult.PASS) {
                    return AuthResult.PASS;
                }
            }
            return AuthResult.FAIL;
        } catch (Exception e) {
            return AuthResult.PERMERROR;
        }
    }

    public String signingDomain(byte[] content) {
        try {
            MimeMessage message = MimeMessageSupport.parse(content);
            String[] dkimHeaders = message.getHeader("DKIM-Signature");
            if (dkimHeaders == null || dkimHeaders.length == 0) {
                return null;
            }
            Map<String, String> tags = parseTags(dkimHeaders[dkimHeaders.length - 1]);
            return tags.get("d");
        } catch (Exception e) {
            return null;
        }
    }

    private AuthResult verifySignature(byte[] content, MimeMessage message, String header) throws Exception {
        Map<String, String> tags = parseTags(header);
        String domain = tags.get("d");
        String selector = tags.get("s");
        String bodyHash = tags.get("bh");
        String signatureValue = tags.get("b");
        String headerList = tags.get("h");
        String algorithm = tags.getOrDefault("a", "rsa-sha256");
        if (!"rsa-sha256".equalsIgnoreCase(algorithm)
                || isBlank(domain) || isBlank(selector) || isBlank(bodyHash)
                || isBlank(signatureValue) || isBlank(headerList)) {
            return AuthResult.PERMERROR;
        }

        String relaxedBody = MimeMessageSupport.relaxedBody(content);
        String computedBodyHash = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(relaxedBody.getBytes(StandardCharsets.ISO_8859_1))
        );
        if (!computedBodyHash.equals(bodyHash)) {
            return AuthResult.FAIL;
        }

        PublicKey publicKey = resolvePublicKey(selector, domain);
        if (publicKey == null) {
            return AuthResult.PERMERROR;
        }

        List<String> headers = Arrays.stream(headerList.split(":"))
                .map(value -> value.toLowerCase(Locale.ROOT).trim())
                .filter(value -> !value.isBlank())
                .toList();
        String unsignedHeader = header.replaceFirst("(?i)b=[^;]*", "b=");
        String signingData = canonicalizedHeaders(message, headers)
                + "dkim-signature:" + MimeMessageSupport.relaxedHeaderValue(unsignedHeader);

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(signingData.getBytes(StandardCharsets.ISO_8859_1));
        byte[] signatureBytes = Base64.getMimeDecoder().decode(signatureValue);
        return signature.verify(signatureBytes) ? AuthResult.PASS : AuthResult.FAIL;
    }

    private PublicKey resolvePublicKey(String selector, String domain) throws Exception {
        String recordName = selector + "._domainkey." + domain;
        String record = dns.txt(recordName).stream()
                .filter(value -> {
                    String lower = value.toLowerCase(Locale.ROOT);
                    return lower.contains("v=dkim1") || lower.contains("p=");
                })
                .findFirst()
                .orElse(null);
        if (record == null) {
            return null;
        }
        Map<String, String> tags = parseTags(record);
        String keyData = tags.get("p");
        if (isBlank(keyData)) {
            return null;
        }
        byte[] der = Base64.getDecoder().decode(keyData.replaceAll("\\s+", ""));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private String canonicalizedHeaders(MimeMessage message, List<String> headers) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (String header : headers) {
            builder.append(MimeMessageSupport.relaxedHeader(message, header));
        }
        return builder.toString();
    }

    private Map<String, String> parseTags(String value) {
        Map<String, String> tags = new HashMap<>();
        for (String part : value.replaceAll("[\\r\\n]+[\\t ]+", "").split(";")) {
            int equals = part.indexOf('=');
            if (equals > 0) {
                tags.put(part.substring(0, equals).trim().toLowerCase(Locale.ROOT),
                        part.substring(equals + 1).trim());
            }
        }
        return tags;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
