package com.sealmail.app.dto.request;

import java.util.List;

public record MailAuthConfigRequest(
        Boolean enabled,
        String authservId,
        Boolean skipPrivateRelay,
        Boolean dkimEnabled,
        String dkimSelector,
        String dkimPrivateKeyPath,
        String dkimPrivateKeyPem,
        Boolean clearDkimPrivateKeyPem,
        List<String> dkimSignedHeaders,
        Boolean spfEnabled,
        Integer spfMaxDnsLookups,
        Boolean spfUseA,
        Boolean spfUseMx,
        List<String> spfIp4,
        List<String> spfIp6,
        List<String> spfIncludes,
        String spfAllPolicy,
        Boolean dmarcEnabled,
        String dmarcPolicy,
        String dmarcAdkim,
        String dmarcAspf,
        Integer dmarcPct,
        String dmarcRua,
        String dmarcRuf,
        String dmarcFailureAction,
        Boolean dmarcQuarantineRejectPolicy
) {
    public MailAuthConfigRequest {
        dkimSignedHeaders = dkimSignedHeaders == null ? null : List.copyOf(dkimSignedHeaders);
        spfIp4 = spfIp4 == null ? null : List.copyOf(spfIp4);
        spfIp6 = spfIp6 == null ? null : List.copyOf(spfIp6);
        spfIncludes = spfIncludes == null ? null : List.copyOf(spfIncludes);
    }
}
