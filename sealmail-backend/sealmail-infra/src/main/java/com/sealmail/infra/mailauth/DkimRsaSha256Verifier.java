package com.sealmail.infra.mailauth;

import com.sealmail.domain.mailauth.AuthenticationMechanism;
import com.sealmail.domain.mailauth.AuthenticationMechanismResult;
import com.sealmail.domain.mailauth.AuthenticationResult;
import com.sealmail.infra.dns.DnsTxtResolver;
import jakarta.mail.internet.MimeMessage;

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

final class DkimRsaSha256Verifier {

    private final DnsTxtResolver dns;

    DkimRsaSha256Verifier(DnsTxtResolver dns) {
        this.dns = dns;
    }

    List<AuthenticationMechanismResult> verify(byte[] content) {
        try {
            MimeMessage message = MailAuthMimeSupport.parse(content);
            String[] dkimHeaders = message.getHeader("DKIM-Signature");
            if (dkimHeaders == null || dkimHeaders.length == 0) {
                return List.of(new AuthenticationMechanismResult(
                        AuthenticationMechanism.DKIM,
                        AuthenticationResult.NONE,
                        null,
                        null,
                        "DKIM-Signature header not found"));
            }
            return Arrays.stream(dkimHeaders)
                    .map(header -> verifySignature(content, message, header))
                    .toList();
        } catch (Exception e) {
            return List.of(new AuthenticationMechanismResult(
                    AuthenticationMechanism.DKIM,
                    AuthenticationResult.PERMERROR,
                    null,
                    null,
                    "DKIM verification failed: " + e.getClass().getSimpleName()));
        }
    }

    private AuthenticationMechanismResult verifySignature(byte[] content, MimeMessage message, String header) {
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
            return result(AuthenticationResult.PERMERROR, domain, selector, "DKIM signature has invalid required tags");
        }
        try {
            String relaxedBody = MailAuthMimeSupport.relaxedBody(content);
            String computedBodyHash = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256")
                            .digest(relaxedBody.getBytes(StandardCharsets.ISO_8859_1)));
            if (!computedBodyHash.equals(bodyHash)) {
                return result(AuthenticationResult.FAIL, domain, selector, "DKIM body hash mismatch");
            }

            PublicKey publicKey = resolvePublicKey(selector, domain);
            if (publicKey == null) {
                return result(AuthenticationResult.PERMERROR, domain, selector, "DKIM public key not found");
            }

            List<String> headers = Arrays.stream(headerList.split(":"))
                    .map(value -> value.toLowerCase(Locale.ROOT).trim())
                    .filter(value -> !value.isBlank())
                    .toList();
            String unsignedHeader = header.replaceFirst("(?i)b=[^;]*", "b=");
            String signingData = canonicalizedHeaders(message, headers)
                    + "dkim-signature:" + MailAuthMimeSupport.relaxedHeaderValue(unsignedHeader);

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(signingData.getBytes(StandardCharsets.ISO_8859_1));
            byte[] signatureBytes = Base64.getMimeDecoder().decode(signatureValue);
            return signature.verify(signatureBytes)
                    ? result(AuthenticationResult.PASS, domain, selector, "DKIM signature verified")
                    : result(AuthenticationResult.FAIL, domain, selector, "DKIM signature mismatch");
        } catch (Exception e) {
            return result(AuthenticationResult.PERMERROR, domain, selector,
                    "DKIM verification failed: " + e.getClass().getSimpleName());
        }
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
        String keyData = parseTags(record).get("p");
        if (isBlank(keyData)) {
            return null;
        }
        byte[] der = Base64.getDecoder().decode(keyData.replaceAll("\\s+", ""));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private String canonicalizedHeaders(MimeMessage message, List<String> headers) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (String header : headers) {
            builder.append(MailAuthMimeSupport.relaxedHeader(message, header));
        }
        return builder.toString();
    }

    private AuthenticationMechanismResult result(AuthenticationResult result,
                                                 String domain,
                                                 String selector,
                                                 String detail) {
        return new AuthenticationMechanismResult(AuthenticationMechanism.DKIM, result, domain, selector, detail);
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
