package com.sealmail.infra.dlp.config;

import com.sealmail.domain.mailsecurity.MailEnvelope;

import java.util.List;

public interface DlpSelectionConfigPort {

    List<DlpSelectionConfig> listSelections();

    DlpSelectionConfig createSelection(DlpSelectionUpdate update);

    DlpSelectionConfig updateSelection(String id, DlpSelectionUpdate update);

    void deleteSelection(String id);

    List<DlpPatternConfig> activePatternsFor(MailEnvelope envelope);

    List<DlpSelectionConfig> matchingSelections(MailEnvelope envelope);

    boolean appliesTo(MailEnvelope envelope);
}
