package com.sealmail.infra.dlp.detector;

import com.sealmail.domain.dlp.DlpContentPart;
import com.sealmail.domain.dlp.DlpDetectorResult;
import com.sealmail.domain.dlp.DlpMatch;
import com.sealmail.domain.dlp.DlpRule;
import com.sealmail.domain.dlp.DlpRuleType;
import com.sealmail.domain.dlp.DlpScanRequest;
import com.sealmail.domain.dlp.spi.DlpDetector;
import com.sealmail.infra.dlp.DlpHashSupport;
import com.sealmail.infra.dlp.config.DlpRuntimeConfigPort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EdmDlpDetector implements DlpDetector {

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}@._+\\-]{3,128}");

    private final DlpRuntimeConfigPort configService;

    public EdmDlpDetector(DlpRuntimeConfigPort configService) {
        this.configService = configService;
    }

    @Override
    public String name() {
        return "EdmDlpDetector";
    }

    @Override
    public int priority() {
        return 40;
    }

    @Override
    public boolean supports(DlpRuleType type) {
        return type == DlpRuleType.EDM;
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
            String datasetId = datasetId(rule);
            if (datasetId.isBlank()) {
                warnings.add("EDM rule has no dataset id: " + rule.name());
                continue;
            }
            if (!configService.edmDatasetEnabled(datasetId)) {
                warnings.add("EDM dataset is disabled or missing: " + datasetId);
                continue;
            }
            Set<String> hashes = configService.edmHashes(datasetId);
            if (hashes.isEmpty()) {
                continue;
            }
            int ruleMatches = 0;
            for (DlpContentPart part : request.content().parts()) {
                if (!rule.scansKind(part.kind())) {
                    continue;
                }
                for (Candidate candidate : candidates(part.text())) {
                    if (hashes.contains(DlpHashSupport.sha256(candidate.normalized()))
                            || (!candidate.compactDigits().isBlank()
                            && hashes.contains(DlpHashSupport.sha256(candidate.compactDigits())))) {
                        matches.add(new DlpMatch(rule, part, candidate.raw(), candidate.start(), candidate.end()));
                        ruleMatches++;
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
        return new DlpDetectorResult(name(), matches, System.currentTimeMillis() - start, warnings);
    }

    private String datasetId(DlpRule rule) {
        if (rule.pattern() != null && !rule.pattern().isBlank()) {
            return rule.pattern().trim();
        }
        return rule.builtinCode() != null ? rule.builtinCode().trim() : "";
    }

    private List<Candidate> candidates(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<Candidate> result = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group();
            String normalized = DlpHashSupport.normalizeExactValue(raw);
            if (normalized.length() >= 3) {
                result.add(new Candidate(raw, normalized, DlpHashSupport.compactDigits(raw), matcher.start(), matcher.end()));
            }
        }
        return List.copyOf(result);
    }

    private record Candidate(String raw, String normalized, String compactDigits, int start, int end) {
    }
}
