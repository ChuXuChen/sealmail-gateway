package com.sealmail.infra.mailauth;

import com.sealmail.domain.mailauth.AuthenticationResult;
import com.sealmail.domain.mailauth.DkimKeyRef;
import com.sealmail.domain.mailauth.DkimKeyResolverPort;
import com.sealmail.domain.mailauth.DkimSelector;
import com.sealmail.domain.mailauth.DkimSigningPolicy;
import com.sealmail.domain.mailauth.DmarcPolicyMode;
import com.sealmail.domain.mailauth.DomainMailAuthPolicy;
import com.sealmail.domain.mailauth.SpfPublicationPolicy;
import com.sealmail.infra.dns.DnsTxtResolver;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DkimRsaSha256SignerVerifierTest {

    @Test
    void signsAndVerifiesWithDnsPublicKey() throws Exception {
        KeyPair pair = rsaPair();
        DkimRsaSha256Signer signer = new DkimRsaSha256Signer(privateKeyResolver(pair));
        DomainMailAuthPolicy policy = policy();

        var signed = signer.sign(message(), policy);

        String publicKeyData = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        DkimRsaSha256Verifier verifier = new DkimRsaSha256Verifier(new FakeDns(Map.of(
                "sealmail._domainkey.example.com", List.of("v=DKIM1; k=rsa; p=" + publicKeyData)
        )));

        var results = verifier.verify(signed.content());

        assertTrue(signed.signed());
        assertEquals(AuthenticationResult.PASS, results.getFirst().result());
        assertEquals("example.com", results.getFirst().domain());
        assertEquals("sealmail", results.getFirst().identity());
    }

    @Test
    void returnsFailWhenSignedBodyChanges() throws Exception {
        KeyPair pair = rsaPair();
        DkimRsaSha256Signer signer = new DkimRsaSha256Signer(privateKeyResolver(pair));
        byte[] signed = signer.sign(message(), policy()).content();
        String tampered = new String(signed, StandardCharsets.ISO_8859_1).replace("body\r\n", "changed\r\n");
        String publicKeyData = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        DkimRsaSha256Verifier verifier = new DkimRsaSha256Verifier(new FakeDns(Map.of(
                "sealmail._domainkey.example.com", List.of("v=DKIM1; k=rsa; p=" + publicKeyData)
        )));

        var results = verifier.verify(tampered.getBytes(StandardCharsets.ISO_8859_1));

        assertEquals(AuthenticationResult.FAIL, results.getFirst().result());
    }

    private static DomainMailAuthPolicy policy() {
        return new DomainMailAuthPolicy(
                "example.com",
                true,
                new DkimSigningPolicy(
                        true,
                        new DkimSelector("sealmail"),
                        new DkimKeyRef("DKIM_PRIVATE_KEY", null),
                        List.of("from", "to", "subject", "date", "message-id")),
                SpfPublicationPolicy.disabled(),
                new com.sealmail.domain.mailauth.DmarcPublicationPolicy(
                        false,
                        DmarcPolicyMode.NONE,
                        DmarcPolicyMode.NONE,
                        null,
                        null,
                        100,
                        null,
                        null),
                null,
                null,
                0);
    }

    private static DkimKeyResolverPort privateKeyResolver(KeyPair pair) {
        return new DkimKeyResolverPort() {
            @Override
            public Optional<String> resolvePrivateKeyPem(DkimKeyRef keyRef) {
                return Optional.of("-----BEGIN PRIVATE KEY-----\n"
                        + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(pair.getPrivate().getEncoded())
                        + "\n-----END PRIVATE KEY-----");
            }

            @Override
            public Optional<String> resolvePublicKeyData(DkimKeyRef keyRef) {
                return Optional.of(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
            }
        };
    }

    private static KeyPair rsaPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static byte[] message() {
        return ("From: sender@example.com\r\n"
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
