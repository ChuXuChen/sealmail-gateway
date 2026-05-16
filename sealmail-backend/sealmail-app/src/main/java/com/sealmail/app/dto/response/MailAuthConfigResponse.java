package com.sealmail.app.dto.response;

import java.time.Instant;
import java.util.List;

public record MailAuthConfigResponse(
        boolean enabled,
        String authservId,
        boolean skipPrivateRelay,
        boolean dkimEnabled,
        String dkimSelector,
        String dkimPrivateKeyPath,
        String dkimPrivateKeySecretRef,
        boolean dkimPrivateKeyConfigured,
        List<String> dkimSignedHeaders,
        boolean spfEnabled,
        int spfMaxDnsLookups,
        boolean spfUseA,
        boolean spfUseMx,
        List<String> spfIp4,
        List<String> spfIp6,
        List<String> spfIncludes,
        String spfAllPolicy,
        boolean dmarcEnabled,
        String dmarcPolicy,
        String dmarcAdkim,
        String dmarcAspf,
        int dmarcPct,
        String dmarcRua,
        String dmarcRuf,
        String dmarcFailureAction,
        boolean dmarcQuarantineRejectPolicy,
        Instant updatedAt
) {
    public MailAuthConfigResponse {
        dkimSignedHeaders = dkimSignedHeaders == null ? List.of() : List.copyOf(dkimSignedHeaders);
        spfIp4 = spfIp4 == null ? List.of() : List.copyOf(spfIp4);
        spfIp6 = spfIp6 == null ? List.of() : List.copyOf(spfIp6);
        spfIncludes = spfIncludes == null ? List.of() : List.copyOf(spfIncludes);
    }
}
