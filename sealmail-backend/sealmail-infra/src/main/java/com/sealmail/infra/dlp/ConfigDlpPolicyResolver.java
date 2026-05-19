package com.sealmail.infra.dlp;

import com.sealmail.domain.dlp.DlpContentBundle;
import com.sealmail.domain.dlp.DlpPolicy;
import com.sealmail.domain.dlp.DlpPolicyMode;
import com.sealmail.domain.dlp.DlpPolicyResolution;
import com.sealmail.domain.dlp.DlpRule;
import com.sealmail.domain.dlp.DlpRuleGroup;
import com.sealmail.domain.dlp.spi.DlpPolicyResolver;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.dlp.config.DlpRuntimeConfigPort;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class ConfigDlpPolicyResolver implements DlpPolicyResolver {

    private final DlpRuntimeConfigPort configService;

    public ConfigDlpPolicyResolver(DlpRuntimeConfigPort configService) {
        this.configService = configService;
    }

    @Override
    public DlpPolicyResolution resolve(MailProcessingContext context, DlpContentBundle content) {
        List<DlpPolicy> policies = configService.activePolicies().stream()
                .filter(policy -> matches(policy, context, content))
                .sorted(Comparator.comparingInt(DlpPolicy::priority).thenComparing(DlpPolicy::name))
                .toList();
        if (!policies.isEmpty()) {
            return resolutionForPolicies(policies);
        }
        return legacyResolution(context);
    }

    @Override
    public DlpPolicyResolution resolvePolicy(String policyId, MailProcessingContext context, DlpContentBundle content) {
        List<DlpPolicy> policies = configService.listPolicies().stream()
                .filter(policy -> policy.id().equals(policyId))
                .filter(policy -> matches(policy, context, content))
                .toList();
        if (policies.isEmpty()) {
            return new DlpPolicyResolution(List.of(), List.of(), List.of(), false);
        }
        return resolutionForPolicies(policies);
    }

    private DlpPolicyResolution resolutionForPolicies(List<DlpPolicy> policies) {
        Map<String, DlpRuleGroup> groupsById = configService.activeRuleGroups().stream()
                .collect(java.util.stream.Collectors.toMap(DlpRuleGroup::id, group -> group));
        Map<String, DlpRule> rulesById = configService.activeRules().stream()
                .collect(java.util.stream.Collectors.toMap(DlpRule::id, rule -> rule));
        LinkedHashMap<String, DlpRuleGroup> selectedGroups = new LinkedHashMap<>();
        LinkedHashMap<String, DlpRule> selectedRules = new LinkedHashMap<>();
        for (DlpPolicy policy : policies) {
            for (String groupId : policy.ruleGroupIds()) {
                DlpRuleGroup group = groupsById.get(groupId);
                if (group == null || !group.enabled()) {
                    continue;
                }
                selectedGroups.putIfAbsent(group.id(), group);
                for (String ruleId : group.ruleIds()) {
                    DlpRule rule = rulesById.get(ruleId);
                    if (rule != null && rule.enabled()) {
                        selectedRules.putIfAbsent(rule.id(), rule);
                    }
                }
            }
        }
        return new DlpPolicyResolution(policies, List.copyOf(selectedGroups.values()), List.copyOf(selectedRules.values()), false);
    }

    private DlpPolicyResolution legacyResolution(MailProcessingContext context) {
        MailEnvelope envelope = context != null ? context.envelope() : null;
        List<DlpRule> rules = configService.activePatternsFor(envelope).stream()
                .map(pattern -> new DlpRule(
                        pattern.id(),
                        pattern.name(),
                        pattern.description(),
                        pattern.type(),
                        pattern.regex(),
                        pattern.builtinCode(),
                        pattern.contentKinds(),
                        pattern.minMatchCount(),
                        pattern.maxEvidenceCount(),
                        pattern.maskingStrategy(),
                        pattern.priority(),
                        pattern.action(),
                        pattern.severity(),
                        pattern.enabled(),
                        pattern.createdAt(),
                        pattern.updatedAt()))
                .toList();
        DlpRuleGroup syntheticGroup = new DlpRuleGroup(
                "legacy-selection",
                "Legacy DLP selection",
                "Rules selected by legacy /selections configuration",
                true,
                100,
                rules.stream().map(DlpRule::id).toList(),
                null,
                null);
        DlpPolicy syntheticPolicy = new DlpPolicy(
                "legacy-selection-policy",
                "Legacy DLP selection policy",
                "Compatibility policy for /patterns and /selections",
                DlpPolicyMode.ENFORCE,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                true,
                100,
                List.of(syntheticGroup.id()),
                null,
                null);
        return rules.isEmpty()
                ? new DlpPolicyResolution(List.of(), List.of(), List.of(), true)
                : new DlpPolicyResolution(List.of(syntheticPolicy), List.of(syntheticGroup), rules, true);
    }

    private boolean matches(DlpPolicy policy, MailProcessingContext context, DlpContentBundle content) {
        if (!policy.enabled()) {
            return false;
        }
        if (policy.attachmentRequired() && (content == null || !content.hasAttachments())) {
            return false;
        }
        if (context == null) {
            return policy.direction() == null
                    && policy.senderDomains().isEmpty()
                    && policy.recipientDomains().isEmpty()
                    && policy.senderAddressPatterns().isEmpty()
                    && policy.recipientAddressPatterns().isEmpty();
        }
        if (policy.direction() != null && context.direction() != policy.direction()) {
            return false;
        }
        MailEnvelope envelope = context.envelope();
        String senderDomain = normalizeDomain(envelope.getSender().getDomain());
        List<String> recipientDomains = envelope.getRecipients().stream()
                .map(EmailAddress::getDomain)
                .map(this::normalizeDomain)
                .toList();
        if (!policy.senderDomains().isEmpty() && !policy.senderDomains().contains(senderDomain)) {
            return false;
        }
        if (!policy.recipientDomains().isEmpty() && recipientDomains.stream().noneMatch(policy.recipientDomains()::contains)) {
            return false;
        }
        if (!policy.senderAddressPatterns().isEmpty()
                && policy.senderAddressPatterns().stream().noneMatch(pattern -> wildcardMatches(pattern, envelope.getSender().getValue()))) {
            return false;
        }
        if (!policy.recipientAddressPatterns().isEmpty()
                && envelope.getRecipients().stream()
                .map(EmailAddress::getValue)
                .noneMatch(email -> policy.recipientAddressPatterns().stream().anyMatch(pattern -> wildcardMatches(pattern, email)))) {
            return false;
        }
        return true;
    }

    private boolean wildcardMatches(String wildcard, String value) {
        String regex = Pattern.quote(wildcard).replace("*", "\\E.*\\Q");
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(value).matches();
    }

    private String normalizeDomain(String domain) {
        return domain == null ? "" : domain.trim().toLowerCase(Locale.ROOT).replaceAll("\\.+$", "");
    }
}
