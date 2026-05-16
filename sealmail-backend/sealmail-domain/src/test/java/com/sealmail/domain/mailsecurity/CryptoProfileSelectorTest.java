package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.KeyUsage;
import com.sealmail.domain.certificate.ValidityPeriod;
import com.sealmail.domain.policy.PreferredAlgorithm;
import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptoProfileSelectorTest {

    private final CryptoProfileSelector selector = new CryptoProfileSelector();

    @Test
    void autoSelectsGmWhenEveryRecipientSupportsGm() {
        EmailAddress recipientA = new EmailAddress("a@example.com");
        EmailAddress recipientB = new EmailAddress("b@example.com");

        CryptoProfileSelector.EncryptionProfilePlan plan = selector.encryptionPlan(
                Map.of(
                        recipientA, List.of(certificate(recipientA, "SM2")),
                        recipientB, List.of(certificate(recipientB, "SM2"))),
                PreferredAlgorithm.AUTO);

        assertTrue(plan.success());
        assertEquals(CryptoProfile.GM, plan.profile());
        assertEquals(Set.of(recipientA, recipientB), plan.certificates().keySet());
    }

    @Test
    void autoFallsBackToStandardWhenGmIsNotSharedButStandardIsShared() {
        EmailAddress recipientA = new EmailAddress("a@example.com");
        EmailAddress recipientB = new EmailAddress("b@example.com");

        CryptoProfileSelector.EncryptionProfilePlan plan = selector.encryptionPlan(
                Map.of(
                        recipientA, List.of(certificate(recipientA, "SM2"), certificate(recipientA, "RSA")),
                        recipientB, List.of(certificate(recipientB, "RSA"))),
                PreferredAlgorithm.AUTO);

        assertTrue(plan.success());
        assertEquals(CryptoProfile.STANDARD, plan.profile());
    }

    @Test
    void failsWhenRecipientsDoNotShareOneProfile() {
        EmailAddress recipientA = new EmailAddress("a@example.com");
        EmailAddress recipientB = new EmailAddress("b@example.com");

        CryptoProfileSelector.EncryptionProfilePlan plan = selector.encryptionPlan(
                Map.of(
                        recipientA, List.of(certificate(recipientA, "SM2")),
                        recipientB, List.of(certificate(recipientB, "RSA"))),
                PreferredAlgorithm.AUTO);

        assertFalse(plan.success());
        assertTrue(plan.failureDetail().contains("无法共享同一加密Profile"));
    }

    @Test
    void explicitProfileFailsWhenAnyRecipientDoesNotSupportIt() {
        EmailAddress recipientA = new EmailAddress("a@example.com");
        EmailAddress recipientB = new EmailAddress("b@example.com");

        CryptoProfileSelector.EncryptionProfilePlan plan = selector.encryptionPlan(
                Map.of(
                        recipientA, List.of(certificate(recipientA, "SM2")),
                        recipientB, List.of(certificate(recipientB, "RSA"))),
                PreferredAlgorithm.GM_ONLY);

        assertFalse(plan.success());
        assertTrue(plan.failureDetail().contains("b@example.com"));
    }

    private static Certificate certificate(EmailAddress owner, String algorithm) {
        Certificate cert = Certificate.importCertificate(
                new CertificateId(UUID.randomUUID().toString()),
                owner,
                "pem-" + UUID.randomUUID(),
                new ValidityPeriod(Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600)),
                Set.of(KeyUsage.ENCRYPTION),
                "CN=issuer",
                "CN=subject",
                BigInteger.ONE,
                "ski-" + UUID.randomUUID()
        );
        cert.trust();
        cert.setAlgorithm(algorithm);
        return cert;
    }
}
