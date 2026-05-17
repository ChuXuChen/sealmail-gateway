package com.sealmail.app.dto.request;

import java.util.List;

public record DomainMailAuthPolicyRequest(
        Boolean enabled,
        Boolean dkimSigningEnabled,
        String dkimSelector,
        String dkimKeySecretRef,
        String dkimKeyPath,
        List<String> dkimSignedHeaders,
        Boolean spfPublishEnabled,
        Boolean spfUseA,
        Boolean spfUseMx,
        List<String> spfIp4,
        List<String> spfIp6,
        List<String> spfIncludes,
        String spfAllPolicy,
        Boolean dmarcPublishEnabled,
        String dmarcPolicy,
        String dmarcSubdomainPolicy,
        String dmarcAdkim,
        String dmarcAspf,
        Integer dmarcPct,
        String dmarcRua,
        String dmarcRuf
) {
    public DomainMailAuthPolicyRequest {
        dkimSignedHeaders = dkimSignedHeaders == null ? null : List.copyOf(dkimSignedHeaders);
        spfIp4 = spfIp4 == null ? null : List.copyOf(spfIp4);
        spfIp6 = spfIp6 == null ? null : List.copyOf(spfIp6);
        spfIncludes = spfIncludes == null ? null : List.copyOf(spfIncludes);
    }
}
