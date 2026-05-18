package com.sealmail.domain.dlp.spi;

import com.sealmail.domain.dlp.DlpMatch;
import com.sealmail.domain.dlp.DlpScanRequest;
import com.sealmail.domain.dlp.DlpUbaAssessment;
import com.sealmail.domain.policy.DispositionAction;

import java.util.List;

public interface DlpUbaRiskEvaluator {

    DlpUbaAssessment assess(DlpScanRequest request, List<DlpMatch> matches, DispositionAction currentAction);
}
