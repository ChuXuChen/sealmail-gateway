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

@Component
public class KeywordDlpDetector implements DlpDetector {

    @Override
    public String name() {
        return "KeywordDlpDetector";
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public boolean supports(DlpRuleType type) {
        return type == DlpRuleType.KEYWORD;
    }

    @Override
    public DlpDetectorResult detect(DlpScanRequest request) {
        long start = System.currentTimeMillis();
        List<DlpMatch> matches = new ArrayList<>();
        for (DlpRule rule : request.rules()) {
            if (!supports(rule.type()) || !rule.enabled() || rule.pattern() == null || rule.pattern().isBlank()) {
                continue;
            }
            List<String> keywords = keywords(rule.pattern());
            int ruleMatches = 0;
            for (var part : request.content().parts()) {
                if (!rule.scansKind(part.kind())) {
                    continue;
                }
                String lowerText = part.text().toLowerCase(Locale.ROOT);
                for (String keyword : keywords) {
                    int index = lowerText.indexOf(keyword.toLowerCase(Locale.ROOT));
                    while (index >= 0) {
                        String matched = part.text().substring(index, Math.min(index + keyword.length(), part.text().length()));
                        matches.add(new DlpMatch(rule, part, matched, index, index + matched.length()));
                        ruleMatches++;
                        if (ruleMatches >= rule.maxEvidenceCount()) {
                            break;
                        }
                        index = lowerText.indexOf(keyword.toLowerCase(Locale.ROOT), index + keyword.length());
                    }
                    if (ruleMatches >= rule.maxEvidenceCount()) {
                        break;
                    }
                }
                if (ruleMatches >= rule.maxEvidenceCount()) {
                    break;
                }
            }
        }
        return new DlpDetectorResult(name(), matches, System.currentTimeMillis() - start, List.of());
    }

    private List<String> keywords(String pattern) {
        return java.util.Arrays.stream(pattern.split("[,\\n]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }
}
