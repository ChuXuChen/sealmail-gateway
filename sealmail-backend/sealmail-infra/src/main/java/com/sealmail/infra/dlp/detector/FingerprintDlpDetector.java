package com.sealmail.infra.dlp.detector;

import com.sealmail.domain.dlp.DlpContentPart;
import com.sealmail.domain.dlp.DlpDetectorResult;
import com.sealmail.domain.dlp.DlpMatch;
import com.sealmail.domain.dlp.DlpRule;
import com.sealmail.domain.dlp.DlpRuleType;
import com.sealmail.domain.dlp.DlpScanRequest;
import com.sealmail.domain.dlp.spi.DlpDetector;
import com.sealmail.infra.dlp.DlpHashSupport;
import com.sealmail.infra.dlp.config.DlpConfigService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class FingerprintDlpDetector implements DlpDetector {

    private final DlpConfigService configService;

    public FingerprintDlpDetector(DlpConfigService configService) {
        this.configService = configService;
    }

    @Override
    public String name() {
        return "FingerprintDlpDetector";
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public boolean supports(DlpRuleType type) {
        return type == DlpRuleType.FINGERPRINT;
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
            String libraryId = libraryId(rule);
            if (libraryId.isBlank()) {
                warnings.add("Fingerprint rule has no library id: " + rule.name());
                continue;
            }
            if (!configService.fingerprintLibraryEnabled(libraryId)) {
                warnings.add("Fingerprint library is disabled or missing: " + libraryId);
                continue;
            }
            Set<String> libraryHashes = configService.fingerprintHashes(libraryId);
            if (libraryHashes.isEmpty()) {
                continue;
            }
            for (DlpContentPart part : request.content().parts()) {
                if (!rule.scansKind(part.kind())) {
                    continue;
                }
                List<String> partHashes = fingerprintChunks(part.text());
                if (partHashes.isEmpty()) {
                    continue;
                }
                long hits = partHashes.stream().filter(libraryHashes::contains).count();
                int threshold = Math.max(1, rule.minMatchCount());
                double ratio = hits / (double) partHashes.size();
                if (hits >= threshold || (hits >= 3 && ratio >= 0.35d)) {
                    String marker = "fingerprint-hit:" + hits + "/" + partHashes.size();
                    matches.add(new DlpMatch(rule, part, marker, 0, Math.min(part.text().length(), 1)));
                    break;
                }
            }
        }
        return new DlpDetectorResult(name(), matches, System.currentTimeMillis() - start, warnings);
    }

    private String libraryId(DlpRule rule) {
        if (rule.pattern() != null && !rule.pattern().isBlank()) {
            return rule.pattern().trim();
        }
        return rule.builtinCode() != null ? rule.builtinCode().trim() : "";
    }

    private List<String> fingerprintChunks(String text) {
        String normalized = java.util.Optional.ofNullable(text)
                .orElse("")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        String[] tokens = normalized.split("\\s+");
        Set<String> hashes = new LinkedHashSet<>();
        if (tokens.length >= 5) {
            for (int i = 0; i <= tokens.length - 5; i++) {
                hashes.add(DlpHashSupport.sha256(String.join(" ", java.util.Arrays.copyOfRange(tokens, i, i + 5))));
            }
            return List.copyOf(hashes);
        }
        String compact = normalized.replace(" ", "");
        int window = Math.min(32, compact.length());
        if (window < 8) {
            return List.of();
        }
        for (int i = 0; i <= compact.length() - window; i++) {
            hashes.add(DlpHashSupport.sha256(compact.substring(i, i + window)));
        }
        return List.copyOf(hashes);
    }
}
