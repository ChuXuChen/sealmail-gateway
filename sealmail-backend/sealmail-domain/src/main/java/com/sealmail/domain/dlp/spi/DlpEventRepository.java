package com.sealmail.domain.dlp.spi;

import com.sealmail.domain.dlp.DlpEvidence;
import com.sealmail.domain.dlp.DlpScanEvent;

import java.util.List;
import java.util.Optional;

public interface DlpEventRepository {

    DlpScanEvent save(DlpScanEvent event, List<DlpEvidence> evidence);

    List<DlpScanEvent> findEvents(
            int offset,
            int limit,
            String action,
            Integer minSeverity,
            String rule,
            String domain);

    long countEvents(String action, Integer minSeverity, String rule, String domain);

    Optional<DlpScanEvent> findEventById(String id);

    List<DlpEvidence> findEvidenceByEventId(String eventId);

    List<DlpEvidence> findEvidenceByQuarantineId(String quarantineId);

    void linkQuarantine(String eventId, String quarantineId);

    void markQuarantineFalsePositive(String quarantineId, String operator, String comment);
}
