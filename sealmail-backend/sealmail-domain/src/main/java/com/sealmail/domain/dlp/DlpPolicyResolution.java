package com.sealmail.domain.dlp;

import java.util.List;

public record DlpPolicyResolution(
        List<DlpPolicy> policies,
        List<DlpRuleGroup> ruleGroups,
        List<DlpRule> rules,
        boolean legacySelection
) {
    public DlpPolicyResolution {
        policies = policies == null ? List.of() : List.copyOf(policies);
        ruleGroups = ruleGroups == null ? List.of() : List.copyOf(ruleGroups);
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public boolean monitorMode() {
        return !policies.isEmpty() && policies.stream().allMatch(policy -> policy.mode() == DlpPolicyMode.MONITOR);
    }

    public List<String> policyIds() {
        return policies.stream().map(DlpPolicy::id).toList();
    }

    public List<String> ruleGroupIds() {
        return ruleGroups.stream().map(DlpRuleGroup::id).toList();
    }
}
