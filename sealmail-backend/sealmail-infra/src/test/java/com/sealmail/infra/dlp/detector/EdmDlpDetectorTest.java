package com.sealmail.infra.dlp.detector;

import com.sealmail.domain.dlp.DlpContentBundle;
import com.sealmail.domain.dlp.DlpContentKind;
import com.sealmail.domain.dlp.DlpContentPart;
import com.sealmail.domain.dlp.DlpMaskingStrategy;
import com.sealmail.domain.dlp.DlpRule;
import com.sealmail.domain.dlp.DlpRuleType;
import com.sealmail.domain.dlp.DlpScanRequest;
import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.infra.dlp.DlpHashSupport;
import com.sealmail.infra.dlp.config.DlpConfigService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EdmDlpDetectorTest {

    @Test
    void matchesOnlyNormalizedHashWithoutDatasetPlaintext() {
        DlpConfigService configService = mock(DlpConfigService.class);
        when(configService.edmDatasetEnabled("dataset-1")).thenReturn(true);
        when(configService.edmHashes("dataset-1")).thenReturn(Set.of(DlpHashSupport.sha256("customer-7788")));
        EdmDlpDetector detector = new EdmDlpDetector(configService);

        var result = detector.detect(new DlpScanRequest(
                new DlpContentBundle(List.of(part("Send Customer-7788 today")), List.of()),
                List.of(rule("dataset-1")),
                List.of(),
                null));

        assertEquals(1, result.matches().size());
        assertEquals("Customer-7788", result.matches().getFirst().matchedText());
    }

    private static DlpRule rule(String datasetId) {
        return new DlpRule(
                "rule-1",
                "EDM",
                "EDM exact match",
                DlpRuleType.EDM,
                datasetId,
                null,
                List.of(),
                1,
                5,
                DlpMaskingStrategy.HASH_ONLY,
                100,
                DispositionAction.QUARANTINE,
                8,
                true,
                null,
                null);
    }

    private static DlpContentPart part(String text) {
        return new DlpContentPart("body", DlpContentKind.BODY_TEXT, null, "text/plain", text.length(), text, false, List.of());
    }
}
