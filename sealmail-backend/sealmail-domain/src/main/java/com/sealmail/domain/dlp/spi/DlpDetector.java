package com.sealmail.domain.dlp.spi;

import com.sealmail.domain.dlp.DlpDetectorResult;
import com.sealmail.domain.dlp.DlpRuleType;
import com.sealmail.domain.dlp.DlpScanRequest;

public interface DlpDetector {

    String name();

    default int priority() {
        return 100;
    }

    boolean supports(DlpRuleType type);

    DlpDetectorResult detect(DlpScanRequest request);
}
