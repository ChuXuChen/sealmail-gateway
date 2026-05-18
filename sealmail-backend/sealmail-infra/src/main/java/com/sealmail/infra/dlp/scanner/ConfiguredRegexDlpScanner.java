package com.sealmail.infra.dlp.scanner;

import com.sealmail.domain.dlp.DlpScanResult;
import com.sealmail.domain.dlp.DlpViolation;
import com.sealmail.domain.dlp.DlpRuleType;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.dlp.spi.DlpContentScanner;
import com.sealmail.infra.dlp.config.DlpConfigService;
import com.sealmail.infra.dlp.config.DlpPatternConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ConfiguredRegexDlpScanner implements DlpContentScanner {

    private final DlpConfigService configService;

    public ConfiguredRegexDlpScanner(DlpConfigService configService) {
        this.configService = configService;
    }

    @Override
    public String getName() {
        return "ConfiguredRegexDlpScanner";
    }

    @Override
    public String getDescription() {
        return "Detects DLP violations using configured regular expressions";
    }

    @Override
    public DlpScanResult scan(String subject, String body, byte[] rawContent) {
        return scan(subject, body, rawContent, null);
    }

    @Override
    public DlpScanResult scan(String subject, String body, byte[] rawContent, MailEnvelope envelope) {
        long startTime = System.currentTimeMillis();
        String content = content(subject, body);
        List<DlpViolation> violations = new ArrayList<>();

        for (DlpPatternConfig rule : configService.activePatternsFor(envelope)) {
            if (rule.type() != DlpRuleType.PATTERN && rule.type() != DlpRuleType.REGEX) {
                continue;
            }
            Matcher matcher = Pattern.compile(rule.regex()).matcher(content);
            if (matcher.find()) {
                violations.add(new DlpViolation(
                        rule.name(),
                        description(rule),
                        abbreviate(matcher.group()),
                        rule.severity(),
                        rule.action()
                ));
            }
        }

        return new DlpScanResult(getName(), violations, System.currentTimeMillis() - startTime);
    }

    private String content(String subject, String body) {
        return (subject != null ? subject : "")
                + "\n"
                + (body != null ? body : "");
    }

    private String description(DlpPatternConfig rule) {
        return rule.description() != null && !rule.description().isBlank()
                ? rule.description()
                : "DLP rule matched: " + rule.name();
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 128 ? value : value.substring(0, 128);
    }
}
