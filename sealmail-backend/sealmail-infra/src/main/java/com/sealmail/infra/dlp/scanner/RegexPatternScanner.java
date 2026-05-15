package com.sealmail.infra.dlp.scanner;

import com.sealmail.domain.dlp.DlpScanResult;
import com.sealmail.domain.dlp.DlpViolation;
import com.sealmail.domain.dlp.spi.DlpContentScanner;
import com.sealmail.domain.policy.DispositionAction;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正则表达式扫描器
 * 使用预定义的正则模式检测敏感内容
 */
public class RegexPatternScanner implements DlpContentScanner {

    private static final List<PatternRule> RULES = List.of(
            // 中国身份证号码
            new PatternRule(
                    "china_id_card",
                    Pattern.compile("\\b[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]\\b"),
                    "检测到身份证号码",
                    8,
                    DispositionAction.QUARANTINE
            ),
            // 中国手机号码
            new PatternRule(
                    "china_phone",
                    Pattern.compile("\\b1[3-9]\\d{9}\\b"),
                    "检测到手机号码",
                    4,
                    DispositionAction.WARN
            ),
            // 银行卡号（简单模式）
            new PatternRule(
                    "bank_card",
                    Pattern.compile("\\b\\d{16,19}\\b"),
                    "检测到银行卡号",
                    7,
                    DispositionAction.QUARANTINE
            ),
            // IPv4 地址
            new PatternRule(
                    "ip_address",
                    Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b"),
                    "检测到 IP 地址",
                    3,
                    DispositionAction.WARN
            ),
            // Email 地址（外部发送检测）
            new PatternRule(
                    "email_address",
                    Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"),
                    "检测到邮箱地址",
                    2,
                    DispositionAction.WARN
            )
    );

    @Override
    public String getName() {
        return "RegexPatternScanner";
    }

    @Override
    public String getDescription() {
        return "Detects sensitive patterns using regular expressions";
    }

    @Override
    public DlpScanResult scan(String subject, String body, byte[] rawContent) {
        long startTime = System.currentTimeMillis();
        List<DlpViolation> violations = new ArrayList<>();

        String content = (subject != null ? subject : "") + " " + (body != null ? body : "");

        for (PatternRule rule : RULES) {
            Matcher matcher = rule.pattern.matcher(content);
            while (matcher.find()) {
                violations.add(new DlpViolation(
                        rule.ruleName,
                        rule.description,
                        matcher.group(),
                        rule.severity,
                        rule.action
                ));
                // 每个规则只记录一次违规
                break;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        return new DlpScanResult(getName(), violations, duration);
    }

    private record PatternRule(String ruleName, Pattern pattern, String description,
                               int severity, DispositionAction action) {
    }
}
