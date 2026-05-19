package com.sealmail.infra.dlp;

import com.sealmail.domain.dlp.DlpMaskingStrategy;
import com.sealmail.domain.dlp.DlpPolicy;
import com.sealmail.domain.dlp.DlpPolicyResolution;
import com.sealmail.domain.dlp.DlpPolicyMode;
import com.sealmail.domain.dlp.DlpRule;
import com.sealmail.domain.dlp.DlpRuleGroup;
import com.sealmail.domain.dlp.DlpRuleType;
import com.sealmail.domain.mail.spi.MailMessageComposer;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.dlp.config.DlpConfigService;
import com.sealmail.infra.dlp.detector.RegexDlpDetector;
import com.sealmail.infra.mail.MimeMailMessageComposer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DlpEvaluationServicePolicyChainTest {

    @Test
    void outboundPolicyRuleGroupRuleChainMatchesBase64TextMailBody() {
        byte[] rawMail = new MimeMailMessageComposer().composeText(new MailMessageComposer.MailDraft(
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.net")),
                "DLP test",
                "hello"));
        MailProcessingContext context = MailProcessingContext.create(new MailEnvelope(
                        "msg-1@example.com",
                        new EmailAddress("sender@example.com"),
                        List.of(new EmailAddress("recipient@example.net")),
                        "127.0.0.1",
                        "helo",
                        Instant.now(),
                        rawMail))
                .withDirection(MailDirection.OUTBOUND);

        DlpConfigService configService = mock(DlpConfigService.class);
        when(configService.activePolicies()).thenReturn(List.of(policy()));
        when(configService.activeRuleGroups()).thenReturn(List.of(ruleGroup()));
        when(configService.activeRules()).thenReturn(List.of(rule(DispositionAction.QUARANTINE)));
        MimeContentExtractor extractor = new MimeContentExtractor();
        var content = extractor.extract(rawMail, context);
        assertTrue(content.parts().stream().anyMatch(part -> part.text().contains("hello")),
                () -> "parts=" + content.parts() + ", warnings=" + content.allWarnings());
        DlpPolicyResolution resolution = new ConfigDlpPolicyResolver(configService).resolve(context, content);
        assertEquals(List.of("rule-1"), resolution.rules().stream().map(DlpRule::id).toList());

        DlpEvaluationService service = new DlpEvaluationService(
                extractor,
                new ConfigDlpPolicyResolver(configService),
                List.of(new RegexDlpDetector()),
                new Sha256DlpEvidenceMasker(),
                mock(com.sealmail.domain.dlp.spi.DlpEventRepository.class),
                null);

        var result = service.evaluate(rawMail, context, false);

        assertTrue(result.hasMatches());
        assertEquals(DispositionAction.QUARANTINE, result.action());
        assertFalse(result.monitorMode());
        assertEquals(List.of("policy-1"), result.policyIds());
        assertEquals(List.of("group-1"), result.ruleGroupIds());
        assertEquals("hello", result.matches().getFirst().matchedText());
    }

    @Test
    void warnRuleMatchesButAllowsDeliveryAction() {
        byte[] rawMail = "Subject: dlp\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\nhello\r\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MailProcessingContext context = MailProcessingContext.create(new MailEnvelope(
                        "msg-1@example.com",
                        new EmailAddress("sender@example.com"),
                        List.of(new EmailAddress("recipient@example.net")),
                        "127.0.0.1",
                        "helo",
                        Instant.now(),
                        rawMail))
                .withDirection(MailDirection.OUTBOUND);

        DlpConfigService configService = mock(DlpConfigService.class);
        when(configService.activePolicies()).thenReturn(List.of(policy()));
        when(configService.activeRuleGroups()).thenReturn(List.of(ruleGroup()));
        when(configService.activeRules()).thenReturn(List.of(rule(DispositionAction.WARN)));
        MimeContentExtractor extractor = new MimeContentExtractor();
        var content = extractor.extract(rawMail, context);
        assertTrue(content.parts().stream().anyMatch(part -> part.text().contains("hello")),
                () -> "parts=" + content.parts() + ", warnings=" + content.allWarnings());
        DlpPolicyResolution resolution = new ConfigDlpPolicyResolver(configService).resolve(context, content);
        assertEquals(List.of("rule-1"), resolution.rules().stream().map(DlpRule::id).toList());

        DlpEvaluationService service = new DlpEvaluationService(
                extractor,
                new ConfigDlpPolicyResolver(configService),
                List.of(new RegexDlpDetector()),
                new Sha256DlpEvidenceMasker(),
                mock(com.sealmail.domain.dlp.spi.DlpEventRepository.class),
                null);

        var result = service.evaluate(rawMail, context, false);

        assertTrue(result.hasMatches());
        assertEquals(DispositionAction.WARN, result.action());
    }

    private static DlpPolicy policy() {
        return new DlpPolicy(
                "policy-1",
                "policy",
                null,
                DlpPolicyMode.ENFORCE,
                MailDirection.OUTBOUND,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                true,
                100,
                List.of("group-1"),
                null,
                null);
    }

    private static DlpRuleGroup ruleGroup() {
        return new DlpRuleGroup(
                "group-1",
                "group",
                null,
                true,
                100,
                List.of("rule-1"),
                null,
                null);
    }

    private static DlpRule rule(DispositionAction action) {
        return new DlpRule(
                "rule-1",
                "hello rule",
                null,
                DlpRuleType.PATTERN,
                "(?:\\Qhello\\E)",
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
    }
}
