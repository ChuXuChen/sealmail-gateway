package com.sealmail.infra.mail.auth;

import com.sealmail.domain.config.SecretReferenceResolver;
import com.sealmail.infra.config.properties.MailAuthProperties;
import com.sealmail.infra.crypto.util.PemUtils;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Component
public class DkimSigner {

    private final MailAuthProperties properties;
    private final SecretReferenceResolver secretReferenceResolver;

    public DkimSigner(MailAuthProperties properties,
                      SecretReferenceResolver secretReferenceResolver) {
        this.properties = properties;
        this.secretReferenceResolver = secretReferenceResolver;
    }

    public byte[] sign(byte[] content, String domain) {
        if (!properties.isEnabled() || !properties.getDkim().isEnabled() || domain == null || domain.isBlank()) {
            return content;
        }
        String keyPem = resolvePrivateKeyPem();
        if (keyPem == null || keyPem.isBlank()) {
            return content;
        }
        try {
            MimeMessage message = MimeMessageSupport.parse(content);
            List<String> signedHeaders = properties.getDkim().getSignedHeaders().stream()
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .toList();
            String body = MimeMessageSupport.relaxedBody(content);
            String bodyHash = Base64.getEncoder().encodeToString(sha256(body.getBytes(StandardCharsets.ISO_8859_1)));
            String dkimWithoutSignature = "v=1; a=rsa-sha256; c=relaxed/relaxed; d=" + domain
                    + "; s=" + properties.getDkim().getSelector()
                    + "; h=" + String.join(":", signedHeaders)
                    + "; bh=" + bodyHash
                    + "; b=";
            String signingData = canonicalizedHeaders(message, signedHeaders)
                    + "dkim-signature:" + MimeMessageSupport.relaxedHeaderValue(dkimWithoutSignature);

            PrivateKey privateKey = PemUtils.parsePrivateKey(keyPem, null);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingData.getBytes(StandardCharsets.ISO_8859_1));
            String signatureValue = Base64.getMimeEncoder(73, "\r\n\t".getBytes(StandardCharsets.ISO_8859_1))
                    .encodeToString(signature.sign());

            return MimeMessageSupport.prependHeader(content, "DKIM-Signature: " + dkimWithoutSignature + signatureValue);
        } catch (Exception e) {
            return content;
        }
    }

    private String canonicalizedHeaders(MimeMessage message, List<String> headers) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (String header : headers) {
            builder.append(MimeMessageSupport.relaxedHeader(message, header));
        }
        return builder.toString();
    }

    private String resolvePrivateKeyPem() {
        if (hasText(properties.getDkim().getPrivateKeyPath())) {
            try {
                return Files.readString(Path.of(properties.getDkim().getPrivateKeyPath()));
            } catch (Exception ignored) {
                return null;
            }
        }
        if (hasText(properties.getDkim().getPrivateKeySecretRef())) {
            try {
                return secretReferenceResolver.resolve(properties.getDkim().getPrivateKeySecretRef());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private byte[] sha256(byte[] data) throws Exception {
        return java.security.MessageDigest.getInstance("SHA-256").digest(data);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
