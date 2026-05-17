package com.sealmail.infra.dlp.detector;

import com.sealmail.domain.dlp.DlpDetectorResult;
import com.sealmail.domain.dlp.DlpMatch;
import com.sealmail.domain.dlp.DlpRule;
import com.sealmail.domain.dlp.DlpRuleType;
import com.sealmail.domain.dlp.DlpScanRequest;
import com.sealmail.domain.dlp.spi.DlpDetector;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class BuiltinDlpDetector implements DlpDetector {

    private static final Map<String, Pattern> BUILTINS = Map.of(
            "CN_ID_CARD", Pattern.compile("\\b[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]\\b"),
            "BANK_CARD", Pattern.compile("\\b\\d{16,19}\\b"),
            "API_KEY", Pattern.compile("(?i)\\b(?:api[_-]?key|secret[_-]?key|access[_-]?token)\\s*[:=]\\s*['\\\"]?[A-Za-z0-9_\\-]{16,}"),
            "PRIVATE_KEY", Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
            "PHONE_CN", Pattern.compile("\\b1[3-9]\\d{9}\\b")
    );

    @Override
    public String name() {
        return "BuiltinDlpDetector";
    }

    @Override
    public int priority() {
        return 30;
    }

    @Override
    public boolean supports(DlpRuleType type) {
        return type == DlpRuleType.BUILTIN;
    }

    @Override
    public DlpDetectorResult detect(DlpScanRequest request) {
        long start = System.currentTimeMillis();
        List<DlpMatch> matches = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (DlpRule rule : request.rules()) {
            if (!supports(rule.type()) || !rule.enabled()) {
                continue;
            }
            Pattern pattern = BUILTINS.get(normalize(rule.builtinCode()));
            if (pattern == null) {
                warnings.add("Unknown builtin DLP rule: " + rule.builtinCode());
                continue;
            }
            int ruleMatches = 0;
            for (var part : request.content().parts()) {
                if (!rule.scansKind(part.kind())) {
                    continue;
                }
                var matcher = pattern.matcher(part.text());
                while (matcher.find()) {
                    matches.add(new DlpMatch(rule, part, matcher.group(), matcher.start(), matcher.end()));
                    ruleMatches++;
                    if (ruleMatches >= rule.maxEvidenceCount()) {
                        break;
                    }
                }
                if (ruleMatches >= rule.maxEvidenceCount()) {
                    break;
                }
            }
        }
        return new DlpDetectorResult(name(), matches, System.currentTimeMillis() - start, warnings);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
