package com.sealmail.infra.dlp.scanner;

import com.sealmail.domain.dlp.DlpScanResult;
import com.sealmail.domain.dlp.DlpViolation;
import com.sealmail.domain.dlp.spi.DlpContentScanner;
import com.sealmail.domain.policy.DispositionAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 关键词扫描器
 * 检测邮件内容中是否包含敏感关键词
 */
public class KeywordScanner implements DlpContentScanner {

    private static final Set<String> KEYWORDS = Set.of(
            "机密", "绝密", "内部资料", "保密", "敏感",
            "confidential", "secret", "internal", "sensitive",
            "工资", "薪酬", "password", "密码", "api key", "apikey"
    );

    @Override
    public String getName() {
        return "KeywordScanner";
    }

    @Override
    public String getDescription() {
        return "Detects sensitive keywords in email content";
    }

    @Override
    public DlpScanResult scan(String subject, String body, byte[] rawContent) {
        long startTime = System.currentTimeMillis();
        List<DlpViolation> violations = new ArrayList<>();

        String content = (subject != null ? subject : "") + " " + (body != null ? body : "");
        String lowerContent = content.toLowerCase();

        for (String keyword : KEYWORDS) {
            int index = lowerContent.indexOf(keyword.toLowerCase());
            if (index >= 0) {
                String matched = content.substring(index, Math.min(index + keyword.length(), content.length()));
                violations.add(new DlpViolation(
                        "keyword_" + keyword,
                        "检测到敏感关键词: " + keyword,
                        matched,
                        5,
                        DispositionAction.WARN
                ));
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        return new DlpScanResult(getName(), violations, duration);
    }
}
