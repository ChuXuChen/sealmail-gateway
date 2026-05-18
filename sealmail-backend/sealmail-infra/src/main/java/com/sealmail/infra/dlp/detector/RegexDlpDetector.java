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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
public class RegexDlpDetector implements DlpDetector {

    @Override
    public String name() {
        return "RegexDlpDetector";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean supports(DlpRuleType type) {
        return type == DlpRuleType.PATTERN || type == DlpRuleType.REGEX;
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
            Pattern pattern;
            try {
                pattern = Pattern.compile(rule.pattern(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            } catch (PatternSyntaxException e) {
                warnings.add("Invalid regex rule " + rule.name() + ": " + e.getMessage());
                continue;
            }
            int ruleMatches = 0;
            for (var part : request.content().parts()) {
                if (!rule.scansKind(part.kind())) {
                    continue;
                }
                Matcher matcher = pattern.matcher(part.text());
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
}
