package com.sealmail.infra.dlp;

import com.sealmail.domain.dlp.DlpScanResult;
import com.sealmail.domain.dlp.DlpViolation;
import com.sealmail.domain.dlp.spi.DlpContentScanner;
import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.infra.dlp.scanner.KeywordScanner;
import com.sealmail.infra.dlp.scanner.RegexPatternScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DLP 服务单元测试
 */
class DlpServiceTest {

    private DlpService dlpService;

    @BeforeEach
    void setUp() {
        List<DlpContentScanner> scanners = Arrays.asList(
                new KeywordScanner(),
                new RegexPatternScanner()
        );
        dlpService = new DlpService(scanners);
    }

    @Test
    void testKeywordDetection() {
        String subject = "机密文件";
        String body = "这是内部资料，包含敏感信息";

        DlpScanResult result = dlpService.scan(subject, body, null);

        assertTrue(result.hasViolations());
        assertTrue(result.getViolations().size() >= 1);
        assertTrue(result.getMaxSeverity() >= 1);
    }

    @Test
    void testIdCardDetection() {
        String subject = "身份信息";
        String body = "我的身份证号是 110101199001011234";

        DlpScanResult result = dlpService.scan(subject, body, null);

        assertTrue(result.hasViolations());
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> v.getRuleName().contains("id_card")));
        assertEquals(8, result.getMaxSeverity());
        assertTrue(result.shouldQuarantine());
    }

    @Test
    void testPhoneNumberDetection() {
        String subject = "联系方式";
        String body = "请联系我：13812345678";

        DlpScanResult result = dlpService.scan(subject, body, null);

        assertTrue(result.hasViolations());
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> v.getRuleName().contains("phone")));
    }

    @Test
    void testIpAddressDetection() {
        String subject = "服务器信息";
        String body = "内部服务器地址：192.168.1.100";

        DlpScanResult result = dlpService.scan(subject, body, null);

        assertTrue(result.hasViolations());
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> v.getRuleName().contains("ip_address")));
    }

    @Test
    void testEmailAddressDetection() {
        String subject = "收件人";
        String body = "发送到 test@example.com";

        DlpScanResult result = dlpService.scan(subject, body, null);

        assertTrue(result.hasViolations());
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> v.getRuleName().contains("email_address")));
    }

    @Test
    void testBankCardDetection() {
        String subject = "银行卡";
        String body = "转账到 6222021234567890123";

        DlpScanResult result = dlpService.scan(subject, body, null);

        assertTrue(result.hasViolations());
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> v.getRuleName().contains("bank_card")));
        assertEquals(7, result.getMaxSeverity());
        assertTrue(result.shouldQuarantine());
    }

    @Test
    void testCleanContentNoViolations() {
        String subject = "问候";
        String body = "你好，今天天气不错";

        DlpScanResult result = dlpService.scan(subject, body, null);

        assertFalse(result.hasViolations());
        assertEquals(0, result.getMaxSeverity());
    }

    @Test
    void testShouldBlock() {
        // 身份证号会触发隔离
        assertTrue(dlpService.shouldBlock(null, "身份证 110101199001011234", null));
        // 普通内容不会触发拦截
        assertFalse(dlpService.shouldBlock(null, "正常内容", null));
    }

    @Test
    void testPasswordDetection() {
        String subject = "账户信息";
        String body = "我的密码是 secret123";

        DlpScanResult result = dlpService.scan(subject, body, null);

        assertTrue(result.getViolations().stream()
                .anyMatch(v -> v.getDescription().toLowerCase().contains("password") ||
                        v.getRuleName().toLowerCase().contains("keyword")));
    }

    @Test
    void testMultipleViolations() {
        String subject = "机密 - 包含身份证";
        String body = "这是机密文件，身份证号 110101199001011234，电话 13812345678";

        DlpScanResult result = dlpService.scan(subject, body, null);

        assertTrue(result.hasViolations());
        assertTrue(result.getViolations().size() >= 3);
        // 最高严重级别应该是身份证的 8
        assertEquals(8, result.getMaxSeverity());
    }

    @Test
    void scannerFailureBecomesBlockViolation() {
        DlpContentScanner failingScanner = new DlpContentScanner() {
            @Override
            public String getName() {
                return "FailingScanner";
            }

            @Override
            public String getDescription() {
                return "throws";
            }

            @Override
            public DlpScanResult scan(String subject, String body, byte[] rawContent) {
                throw new IllegalStateException("scanner down");
            }
        };
        DlpService service = new DlpService(List.of(failingScanner));

        DlpScanResult result = service.scan("subject", "body", null);

        assertTrue(result.hasViolations());
        assertEquals(DispositionAction.BLOCK, result.getFinalAction());
        assertTrue(result.shouldBlock());
    }

    @Test
    void finalActionUsesExplicitActionPriority() {
        DlpScanResult result = new DlpScanResult("test", List.of(
                new DlpViolation("warn", "warn matched", "a", 10, DispositionAction.WARN),
                new DlpViolation("encrypt", "encrypt matched", "b", 1, DispositionAction.MUST_ENCRYPT),
                new DlpViolation("quarantine", "quarantine matched", "c", 1, DispositionAction.QUARANTINE),
                new DlpViolation("block", "block matched", "d", 1, DispositionAction.BLOCK)
        ), 1);

        assertEquals(DispositionAction.BLOCK, result.getFinalAction());
        assertEquals("block", result.getPrimaryViolation().orElseThrow().getRuleName());
    }

}
