package com.sealmail.domain.dlp;

import com.sealmail.domain.mailsecurity.MailProcessingContext;

import java.util.List;

public record DlpScanRequest(
        DlpContentBundle content,
        List<DlpRule> rules,
        List<DlpPolicy> policies,
        MailProcessingContext context
) {
    public DlpScanRequest {
        content = content != null ? content : new DlpContentBundle(List.of(), List.of());
        rules = rules == null ? List.of() : List.copyOf(rules);
        policies = policies == null ? List.of() : List.copyOf(policies);
    }
}
