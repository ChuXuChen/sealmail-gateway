package com.sealmail.domain.dlp.spi;

import com.sealmail.domain.dlp.DlpEvidence;
import com.sealmail.domain.dlp.DlpMatch;

public interface DlpEvidenceMasker {

    DlpEvidence mask(String eventId, DlpMatch match);
}
