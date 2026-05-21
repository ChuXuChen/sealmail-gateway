package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailauth.AuthenticationMechanism;
import com.sealmail.domain.mailauth.AuthenticationMechanismResult;
import com.sealmail.domain.mailauth.AuthenticationResult;
import com.sealmail.domain.mailauth.AuthenticationResultSet;
import com.sealmail.domain.mailauth.MailAuthDecision;
import com.sealmail.domain.dlp.DlpContentKind;
import com.sealmail.domain.dlp.DlpContentPart;
import com.sealmail.domain.dlp.DlpEvaluationResult;
import com.sealmail.domain.dlp.DlpMaskingStrategy;
import com.sealmail.domain.dlp.DlpMatch;
import com.sealmail.domain.dlp.DlpRule;
import com.sealmail.domain.dlp.DlpRuleType;
import com.sealmail.domain.dlp.DlpUbaRiskLevel;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessing;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingDecision;
import com.sealmail.domain.mailsecurity.MailProcessingRepository;
import com.sealmail.domain.mailsecurity.MailProcessingStatusSnapshot;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailProcessingStatusServiceTest {

    @Test
    void recordsAuthenticationFailureSnapshot() {
        InMemoryMailProcessingRepository repository = new InMemoryMailProcessingRepository();
        MailProcessing processing = processing();
        repository.save(processing);
        MailProcessingStatusService service = new MailProcessingStatusService(repository);

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

        service.recordMailAuthentication(context(processing), authResult);

        MailProcessingStatusSnapshot snapshot = processing.getStatusSnapshot();
        assertEquals(MailProcessingStatusSnapshot.FAIL, snapshot.mailAuth().status());
        assertEquals("mail-auth", snapshot.failure().step());
        assertEquals("AUTHENTICATION", snapshot.failure().errorType());
    }

    @Test
    void recordsMissingRecipientCertificateFromRoutingContext() {
        InMemoryMailProcessingRepository repository = new InMemoryMailProcessingRepository();
        MailProcessing processing = processing();
        repository.save(processing);
        MailProcessingStatusService service = new MailProcessingStatusService(repository);
        MailProcessingContext context = context(processing)
                .withDirection(MailDirection.OUTBOUND)
                .withDecision(MailProcessingDecision.none().withEncryptionRequired(true));

        service.recordRouting(context);

        MailProcessingStatusSnapshot snapshot = processing.getStatusSnapshot();
        assertEquals(MailProcessingStatusSnapshot.FAIL, snapshot.certificate().status());
        assertTrue(snapshot.certificate().recipientMissing());
        assertEquals(List.of("recipient@example.com"), snapshot.certificate().missingRecipients());
    }

    @Test
    void recordsRelaySuccessAsDeliveredDisposition() {
        InMemoryMailProcessingRepository repository = new InMemoryMailProcessingRepository();
        MailProcessing processing = processing();
        repository.save(processing);
        MailProcessingStatusService service = new MailProcessingStatusService(repository);

        service.recordRelaySuccess(context(processing));

        MailProcessingStatusSnapshot snapshot = processing.getStatusSnapshot();
        assertEquals(MailProcessingStatusSnapshot.DELIVERED, snapshot.delivery().status());
        assertEquals(MailProcessingStatusSnapshot.DELIVERED, snapshot.finalDisposition().status());
        assertEquals(ProcessingResult.SUCCESS.name(), snapshot.finalDisposition().result());
    }

    @Test
    void recordsDlpQuarantineAsDlpFailureWithoutSensitiveEvidence() {
        InMemoryMailProcessingRepository repository = new InMemoryMailProcessingRepository();
        MailProcessing processing = processing();
        repository.save(processing);
        MailProcessingStatusService service = new MailProcessingStatusService(repository);

        service.recordDlpEvaluation(
                context(processing),
                dlpResult(DispositionAction.QUARANTINE),
                "rule matched");

        MailProcessingStatusSnapshot snapshot = processing.getStatusSnapshot();
        assertEquals(MailProcessingStatusSnapshot.QUARANTINED, snapshot.dlp().status());
        assertEquals("QUARANTINE", snapshot.dlp().action());
        assertEquals(1, snapshot.dlp().matchCount());
        assertEquals(List.of("rule"), snapshot.dlp().ruleNames());
        assertEquals("event-1", snapshot.dlp().eventId());
        assertEquals("dlp", snapshot.failure().step());
        assertEquals("DLP", snapshot.failure().errorType());
    }

    private static MailProcessing processing() {
        return MailProcessing.create(envelope(), MailDirection.OUTBOUND);
    }

    private static MailProcessingContext context(MailProcessing processing) {
        return MailProcessingContext.create(processing.getEnvelope())
                .withProcessingId(processing.getId())
                .withDirection(processing.getDirection());
    }

    private static MailEnvelope envelope() {
        return new MailEnvelope(
                "msg-1@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                "helo",
                Instant.now(),
                "body".getBytes());
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

    private static class InMemoryMailProcessingRepository implements MailProcessingRepository {
        private MailProcessing value;

        @Override
        public MailProcessing save(MailProcessing mailProcessing) {
            this.value = mailProcessing;
            return mailProcessing;
        }

        @Override
        public Optional<MailProcessing> findById(String id) {
            return value != null && value.getId().equals(id) ? Optional.of(value) : Optional.empty();
        }

        @Override
        public List<MailProcessing> findByMessageId(String messageId) {
            return List.of();
        }

        @Override
        public List<MailProcessing> findByResult(ProcessingResult result) {
            return List.of();
        }

        @Override
        public List<MailProcessing> findRecent(int page, int size) {
            return value != null ? List.of(value) : List.of();
        }

        @Override
        public long count() {
            return value != null ? 1 : 0;
        }
    }
}
