package com.sealmail.infra.dlp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.dlp.DlpContentBundle;
import com.sealmail.domain.dlp.DlpContentKind;
import com.sealmail.domain.dlp.DlpContentPart;
import com.sealmail.domain.dlp.DlpMaskingStrategy;
import com.sealmail.domain.dlp.DlpMatch;
import com.sealmail.domain.dlp.DlpRule;
import com.sealmail.domain.dlp.DlpRuleType;
import com.sealmail.domain.dlp.DlpScanRequest;
import com.sealmail.domain.dlp.DlpUbaRiskLevel;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.domain.shared.model.EmailAddress;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DlpUbaRiskServiceTest {

    @Test
    void highRiskDlpWarnIsUpgradedToQuarantineWithoutBlockingDirectly() {
        DlpUbaRiskService service = new DlpUbaRiskService(mock(EntityManager.class), new ObjectMapper());
        DlpRule rule = new DlpRule(
                "rule-1",
                "high",
                "high severity",
                DlpRuleType.PATTERN,
                "secret",
                null,
                List.of(),
                1,
                5,
                DlpMaskingStrategy.DEFAULT,
                100,
                DispositionAction.WARN,
                9,
                true,
                null,
                null);
        DlpContentPart part = new DlpContentPart("body", DlpContentKind.BODY_TEXT, null, "text/plain", 6, "secret", false, List.of());
        DlpMatch match = new DlpMatch(rule, part, "secret", 0, 6);
        DlpScanRequest request = new DlpScanRequest(
                new DlpContentBundle(List.of(part), List.of()),
                List.of(rule),
                List.of(),
                MailProcessingContext.create(new MailEnvelope(
                                "msg-1@example.com",
                                new EmailAddress("alice@example.com"),
                                List.of(new EmailAddress("bob@new-domain.test")),
                                "127.0.0.1",
                                "helo",
                                Instant.now()))
                        .withDirection(MailDirection.OUTBOUND));

        var assessment = service.assess(request, List.of(match, match), DispositionAction.WARN);

        assertEquals(DlpUbaRiskLevel.HIGH, assessment.riskLevel());
        assertTrue(assessment.actionUpgraded());
        assertEquals(DispositionAction.QUARANTINE, assessment.upgradedAction());
    }
}
