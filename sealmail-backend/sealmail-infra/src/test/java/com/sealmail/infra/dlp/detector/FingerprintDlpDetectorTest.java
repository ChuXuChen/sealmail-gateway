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

class FingerprintDlpDetectorTest {

    @Test
    void triggersWhenDocumentChunksOverlapLibrary() {
        String document = "alpha beta gamma delta epsilon zeta eta theta";
        DlpConfigService configService = mock(DlpConfigService.class);
        when(configService.fingerprintLibraryEnabled("library-1")).thenReturn(true);
        when(configService.fingerprintHashes("library-1")).thenReturn(Set.of(
                DlpHashSupport.sha256("alpha beta gamma delta epsilon"),
                DlpHashSupport.sha256("beta gamma delta epsilon zeta"),
                DlpHashSupport.sha256("gamma delta epsilon zeta eta")
        ));
        FingerprintDlpDetector detector = new FingerprintDlpDetector(configService);

        var result = detector.detect(new DlpScanRequest(
                new DlpContentBundle(List.of(part(document)), List.of()),
                List.of(rule("library-1")),
                List.of(),
                null));

        assertEquals(1, result.matches().size());
        assertEquals("fingerprint-hit:3/4", result.matches().getFirst().matchedText());
    }

    private static DlpRule rule(String libraryId) {
        return new DlpRule(
                "rule-1",
                "Fingerprint",
                "fingerprint match",
                DlpRuleType.FINGERPRINT,
                libraryId,
                null,
                List.of(),
                3,
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
