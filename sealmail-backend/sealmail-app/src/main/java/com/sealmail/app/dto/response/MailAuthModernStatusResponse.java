package com.sealmail.app.dto.response;

import java.util.List;

public record MailAuthModernStatusResponse(
        boolean enabled,
        String authservId,
        String trustedProxyMode,
        String failureDefaultAction,
        long domainPolicyCount,
        long dkimEnabledDomainCount,
        List<MailAuthDnsProbeResponse> recentDnsProbes
) {
    public MailAuthModernStatusResponse {
        recentDnsProbes = recentDnsProbes == null ? List.of() : List.copyOf(recentDnsProbes);
    }
}
