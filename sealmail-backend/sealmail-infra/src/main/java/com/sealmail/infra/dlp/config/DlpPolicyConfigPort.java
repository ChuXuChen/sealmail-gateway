package com.sealmail.infra.dlp.config;

import com.sealmail.domain.dlp.DlpPolicy;
import com.sealmail.domain.dlp.DlpRuleGroup;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpPolicySettingsUpdate;
import com.sealmail.domain.dlp.config.DlpConfigPort.DlpRuleGroupSettingsUpdate;

import java.util.List;

public interface DlpPolicyConfigPort {

    List<DlpRuleGroup> listRuleGroups();

    List<DlpRuleGroup> activeRuleGroups();

    DlpRuleGroup createRuleGroupConfig(DlpRuleGroupSettingsUpdate update);

    DlpRuleGroup updateRuleGroupConfig(String id, DlpRuleGroupSettingsUpdate update);

    void deleteRuleGroup(String id);

    List<DlpPolicy> listPolicies();

    List<DlpPolicy> activePolicies();

    DlpPolicy createPolicyConfig(DlpPolicySettingsUpdate update);

    DlpPolicy updatePolicyConfig(String id, DlpPolicySettingsUpdate update);

    void deletePolicy(String id);
}
