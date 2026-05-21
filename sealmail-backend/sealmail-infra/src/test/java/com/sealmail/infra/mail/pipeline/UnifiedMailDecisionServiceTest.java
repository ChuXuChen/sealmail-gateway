package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.dlp.DlpContentKind;
import com.sealmail.domain.dlp.DlpContentPart;
import com.sealmail.domain.dlp.DlpEvaluationResult;
import com.sealmail.domain.dlp.DlpMaskingStrategy;
import com.sealmail.domain.dlp.DlpMatch;
import com.sealmail.domain.dlp.DlpRule;
import com.sealmail.domain.dlp.DlpRuleType;
import com.sealmail.domain.dlp.DlpUbaRiskLevel;
import com.sealmail.domain.mailauth.AuthenticationMechanism;
import com.sealmail.domain.mailauth.AuthenticationMechanismResult;
import com.sealmail.domain.mailauth.AuthenticationResult;
import com.sealmail.domain.mailauth.AuthenticationResultSet;
import com.sealmail.domain.mailauth.MailAuthDecision;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedMailDecisionServiceTest {

    private final UnifiedMailDecisionService service = new UnifiedMailDecisionService();

    @Test
    void mailAuthenticationFailureIsConvertedToUnifiedQuarantineDecision() {
        MailProcessingContext context = context();
        AuthenticationResultSet authResult = new AuthenticationResultSet(
                new AuthenticationMechanismResult(
                        AuthenticationMechanism.SPF,
                        AuthenticationResult.FAIL,
                        "example.com",
                        "203.0.113.10",
                        "SPF fail"),
                List.of(),
                new AuthenticationMechanismResult(
                        AuthenticationMechanism.DMARC,
                        AuthenticationResult.FAIL,
                        "example.com",
                        "reject",
                        "DMARC reject"),
                MailAuthDecision.applyPolicy("DMARC reject"),
                null);

        MailProcessingContext updated = service.applyMailAuthentication(context, authResult);

        assertEquals(AuthenticationResult.FAIL, updated.mailAuthResults().spf().result());
        assertTrue(updated.decision().requiresQuarantine());
        assertEquals("EMAIL_AUTH_FAILED", updated.decision().quarantine().reason());
        assertEquals(MailRecordDisposition.EXCEPTION, updated.recordDisposition());
    }

    @Test
    void dlpWarnAnnotatesContextWithoutChangingFinalDisposition() {
        MailProcessingContext updated = service.applyDlpEvaluation(
                context(),
                dlpResult(DispositionAction.WARN),
                "rule matched");

        assertFalse(updated.decision().requiresQuarantine());
        assertFalse(updated.decision().mustEncrypt());
        assertEquals(DispositionAction.WARN, updated.decision().dlpDecision().action());
        assertEquals("event-1", updated.decision().dlpDecision().eventId());
    }

    @Test
    void dlpQuarantineSetsDispositionForQuarantineStore() {
        MailProcessingContext updated = service.applyDlpEvaluation(
                context(),
                dlpResult(DispositionAction.QUARANTINE),
                "rule matched");

        assertTrue(updated.decision().requiresQuarantine());
        assertEquals("POLICY_VIOLATION", updated.decision().quarantine().reason());
        assertEquals(MailRecordDisposition.DLP_QUARANTINE, updated.recordDisposition());
    }

    @Test
    void dlpMustEncryptKeepsDeliveryOpenAndRequiresEncryption() {
        MailProcessingContext updated = service.applyDlpEvaluation(
                context(),
                dlpResult(DispositionAction.MUST_ENCRYPT),
                "rule matched");

        assertFalse(updated.decision().requiresQuarantine());
        assertTrue(updated.decision().mustEncrypt());
    }

    private static MailProcessingContext context() {
        return MailProcessingContext.create(new MailEnvelope(
                "msg-1@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                "helo",
                Instant.now(),
                "body".getBytes()));
    }

    private static DlpEvaluationResult dlpResult(DispositionAction action) {
        DlpRule rule = new DlpRule(
                "rule-id",
                "rule",
                "rule matched",
                DlpRuleType.KEYWORD,
                "matched",
                null,
                List.of(),
                1,
                5,
                DlpMaskingStrategy.DEFAULT,
                100,
                action,
                5,
                true,
                null,
                null);
        DlpContentPart part = new DlpContentPart(
                "body",
                DlpContentKind.BODY_TEXT,
                null,
                "text/plain",
                7,
                "matched",
                false,
                List.of());
        DlpMatch match = new DlpMatch(rule, part, "matched", 0, 7);
        return new DlpEvaluationResult(
                "event-1",
                action,
                action,
                5,
                List.of(match),
                List.of(),
                List.of(),
                List.of("policy-1"),
                List.of("group-1"),
                false,
                1,
                DlpUbaRiskLevel.LOW,
                List.of(),
                false);
    }
}
