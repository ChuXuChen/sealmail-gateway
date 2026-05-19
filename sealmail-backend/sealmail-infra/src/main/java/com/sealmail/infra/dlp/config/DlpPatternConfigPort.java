package com.sealmail.infra.dlp.config;

import com.sealmail.domain.dlp.DlpRule;

import java.util.List;

public interface DlpPatternConfigPort {

    List<DlpPatternConfig> listPatterns();

    List<DlpRule> listRules();

    List<DlpRule> activeRules();

    List<DlpPatternConfig> activePatterns();

    DlpPatternConfig createPattern(DlpPatternUpdate update);

    DlpPatternConfig updatePattern(String id, DlpPatternUpdate update);

    void deletePattern(String id);
}
