package com.sealmail.app.dto.response;

import com.sealmail.domain.mailauth.DomainMailAuthPolicy;

import java.time.Instant;
import java.util.List;

public record DomainMailAuthPolicyResponse(
        String domainName,
        boolean enabled,
        boolean dkimSigningEnabled,
        String dkimSelector,
        String dkimKeySecretRef,
        String dkimKeyPath,
        boolean dkimKeyConfigured,
        List<String> dkimSignedHeaders,
        boolean spfPublishEnabled,
        boolean spfUseA,
        boolean spfUseMx,
        List<String> spfIp4,
        List<String> spfIp6,
        List<String> spfIncludes,
        String spfAllPolicy,
        boolean dmarcPublishEnabled,
        String dmarcPolicy,
        String dmarcSubdomainPolicy,
        String dmarcAdkim,
        String dmarcAspf,
        int dmarcPct,
        String dmarcRua,
        String dmarcRuf,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    public DomainMailAuthPolicyResponse {
        dkimSignedHeaders = dkimSignedHeaders == null ? List.of() : List.copyOf(dkimSignedHeaders);
        spfIp4 = spfIp4 == null ? List.of() : List.copyOf(spfIp4);
        spfIp6 = spfIp6 == null ? List.of() : List.copyOf(spfIp6);
        spfIncludes = spfIncludes == null ? List.of() : List.copyOf(spfIncludes);
    }

    public static DomainMailAuthPolicyResponse from(DomainMailAuthPolicy policy) {
        return new DomainMailAuthPolicyResponse(
                policy.domainName(),
                policy.enabled(),
                policy.dkimSigningPolicy().enabled(),
                policy.dkimSigningPolicy().selector().value(),
                policy.dkimSigningPolicy().keyRef().secretRef(),
                policy.dkimSigningPolicy().keyRef().path(),
                policy.dkimSigningPolicy().keyRef().configured(),
                policy.dkimSigningPolicy().signedHeaders(),
                policy.spfPublicationPolicy().enabled(),
                policy.spfPublicationPolicy().useA(),
                policy.spfPublicationPolicy().useMx(),
                policy.spfPublicationPolicy().ip4(),
                policy.spfPublicationPolicy().ip6(),
                policy.spfPublicationPolicy().includes(),
                policy.spfPublicationPolicy().allPolicy(),
                policy.dmarcPublicationPolicy().enabled(),
                policy.dmarcPublicationPolicy().policy().tagValue(),
                policy.dmarcPublicationPolicy().subdomainPolicy().tagValue(),
                policy.dmarcPublicationPolicy().dkimAlignment().tagValue(),
                policy.dmarcPublicationPolicy().spfAlignment().tagValue(),
                policy.dmarcPublicationPolicy().pct(),
                policy.dmarcPublicationPolicy().rua(),
                policy.dmarcPublicationPolicy().ruf(),
                policy.createdAt(),
                policy.updatedAt(),
                policy.version());
    }
}
