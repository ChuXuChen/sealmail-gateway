package com.sealmail.infra.mailauth;

import com.sealmail.domain.mailauth.DkimKeyResolverPort;
import com.sealmail.domain.mailauth.DkimSigningPort;
import com.sealmail.domain.mailauth.DomainMailAuthPolicy;
import com.sealmail.domain.mailauth.SigningResult;
import com.sealmail.infra.crypto.util.PemUtils;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;
import java.util.List;

@Component
public class DkimRsaSha256Signer implements DkimSigningPort {

    private final DkimKeyResolverPort keyResolver;

    public DkimRsaSha256Signer(DkimKeyResolverPort keyResolver) {
        this.keyResolver = keyResolver;
    }

    @Override
    public SigningResult sign(byte[] rawContent, DomainMailAuthPolicy policy) {
        if (rawContent == null || policy == null || !policy.dkimSigningPolicy().signingReady()) {
            return new SigningResult(rawContent, false, policy != null ? policy.domainName() : null, null,
                    "DKIM signing policy is not ready");
        }
        return keyResolver.resolvePrivateKeyPem(policy.dkimSigningPolicy().keyRef())
                .map(keyPem -> signWithKey(rawContent, policy, keyPem))
                .orElseGet(() -> new SigningResult(
                        rawContent,
                        false,
                        policy.domainName(),
                        policy.dkimSigningPolicy().selector().value(),
                        "DKIM private key could not be resolved"));
    }

    private SigningResult signWithKey(byte[] rawContent, DomainMailAuthPolicy policy, String keyPem) {
        try {
            MimeMessage message = MailAuthMimeSupport.parse(rawContent);
            List<String> signedHeaders = policy.dkimSigningPolicy().signedHeaders();
            String relaxedBody = MailAuthMimeSupport.relaxedBody(rawContent);
            String bodyHash = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256")
                            .digest(relaxedBody.getBytes(StandardCharsets.ISO_8859_1)));
            String dkimWithoutSignature = "v=1; a=rsa-sha256; c=relaxed/relaxed; d=" + policy.domainName()
                    + "; s=" + policy.dkimSigningPolicy().selector().value()
                    + "; h=" + String.join(":", signedHeaders)
                    + "; bh=" + bodyHash
                    + "; b=";

            String signingData = canonicalizedHeaders(message, signedHeaders)
                    + "dkim-signature:" + MailAuthMimeSupport.relaxedHeaderValue(dkimWithoutSignature);
            PrivateKey privateKey = PemUtils.parsePrivateKey(keyPem, null);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingData.getBytes(StandardCharsets.ISO_8859_1));
            String signatureValue = Base64.getMimeEncoder(73, "\r\n\t".getBytes(StandardCharsets.ISO_8859_1))
                    .encodeToString(signature.sign());
            return new SigningResult(
                    MailAuthMimeSupport.prependHeader(rawContent, "DKIM-Signature: " + dkimWithoutSignature + signatureValue),
                    true,
                    policy.domainName(),
                    policy.dkimSigningPolicy().selector().value(),
                    "DKIM signature added");
        } catch (Exception e) {
            return new SigningResult(
                    rawContent,
                    false,
                    policy.domainName(),
                    policy.dkimSigningPolicy().selector().value(),
                    "DKIM signing failed: " + e.getClass().getSimpleName());
        }
    }

    private String canonicalizedHeaders(MimeMessage message, List<String> headers) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (String header : headers) {
            builder.append(MailAuthMimeSupport.relaxedHeader(message, header));
        }
        return builder.toString();
    }
}
