package com.sealmail.infra.dlp.config;

import com.sealmail.domain.dlp.DlpPolicy;
import com.sealmail.domain.dlp.DlpRule;
import com.sealmail.domain.dlp.DlpRuleGroup;
import com.sealmail.domain.mailsecurity.MailEnvelope;

import java.util.List;
import java.util.Set;

public interface DlpRuntimeConfigPort {

    List<DlpPatternConfig> activePatternsFor(MailEnvelope envelope);

    List<DlpRule> activeRules();

    List<DlpRuleGroup> activeRuleGroups();

    List<DlpPolicy> activePolicies();

    List<DlpPolicy> listPolicies();

    boolean edmDatasetEnabled(String datasetId);

    Set<String> edmHashes(String datasetId);

    boolean fingerprintLibraryEnabled(String libraryId);

    Set<String> fingerprintHashes(String libraryId);
}
