package com.sealmail.infra.mail.auth;

import com.sealmail.domain.config.SecretReferenceResolver;
import com.sealmail.infra.config.properties.MailAuthProperties;
import com.sealmail.infra.dns.DnsTxtResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyDkimSignerVerifierCharacterizationTest {

    @TempDir
    Path tempDir;

    @Test
    void signsWithCurrentRelaxedRsaSha256HeaderAndVerifiesAgainstDnsPublicKey() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        Path privateKeyFile = tempDir.resolve("dkim-test-private-key.pem");
        Files.writeString(privateKeyFile, pkcs8Pem(keyPair.getPrivate()));

        MailAuthProperties properties = new MailAuthProperties();
        properties.getDkim().setSelector("sealmail");
        properties.getDkim().setPrivateKeyPath(privateKeyFile.toString());
        properties.getDkim().setSignedHeaders(List.of("from", "to", "subject", "date", "message-id"));
        DkimSigner signer = new DkimSigner(properties, unresolvedSecrets());

        byte[] message = """
                From: Alice <alice@example.com>\r
                To: Bob <bob@example.org>\r
                Subject: DKIM characterization\r
                Date: Tue, 01 Apr 2025 12:00:00 +0000\r
                Message-Id: <legacy-dkim@example.com>\r
                \r
                body\r
                """.getBytes(StandardCharsets.ISO_8859_1);

        byte[] signed = signer.sign(message, "example.com");

        String signedText = new String(signed, StandardCharsets.ISO_8859_1);
        assertTrue(signedText.startsWith("DKIM-Signature: "));
        assertTrue(signedText.contains("a=rsa-sha256"));
        assertTrue(signedText.contains("c=relaxed/relaxed"));
        assertTrue(signedText.contains("d=example.com"));
        assertTrue(signedText.contains("s=sealmail"));
        assertTrue(signedText.contains("h=from:to:subject:date:message-id"));

        DkimVerifier verifier = new DkimVerifier(new FakeDnsTxtResolver(Map.of(
                "sealmail._domainkey.example.com",
                List.of("v=DKIM1; k=rsa; p=" + publicKeyData(keyPair.getPublic()))
        )));

        assertEquals(AuthResult.PASS, verifier.verify(signed));
        assertEquals("example.com", verifier.signingDomain(signed));
    }

    @Test
    void signingFailureReturnsOriginalContentUnchanged() {
        MailAuthProperties properties = new MailAuthProperties();
        properties.getDkim().setPrivateKeyPath(tempDir.resolve("missing.pem").toString());
        byte[] message = "From: alice@example.com\r\n\r\nbody\r\n".getBytes(StandardCharsets.ISO_8859_1);

        byte[] signed = new DkimSigner(properties, unresolvedSecrets()).sign(message, "example.com");

        assertEquals(new String(message, StandardCharsets.ISO_8859_1),
                new String(signed, StandardCharsets.ISO_8859_1));
    }

    @Test
    void missingDkimSignatureIsNone() {
        DkimVerifier verifier = new DkimVerifier(new FakeDnsTxtResolver(Map.of()));

        assertEquals(AuthResult.NONE, verifier.verify(
                "From: alice@example.com\r\n\r\nbody\r\n".getBytes(StandardCharsets.ISO_8859_1)));
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String pkcs8Pem(PrivateKey privateKey) {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(privateKey.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    private static String publicKeyData(PublicKey publicKey) throws Exception {
        RSAPrivateCrtKey ignored = null;
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    private static SecretReferenceResolver unresolvedSecrets() {
        return secretRef -> {
            throw new IllegalArgumentException("Unexpected secret ref in characterization test: " + secretRef);
        };
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
