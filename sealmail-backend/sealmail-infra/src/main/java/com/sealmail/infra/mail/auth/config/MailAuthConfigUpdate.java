package com.sealmail.infra.mail.auth.config;

import java.util.List;

public record MailAuthConfigUpdate(
        Boolean enabled,
        String authservId,
        Boolean skipPrivateRelay,
        Boolean dkimEnabled,
        String dkimSelector,
        String dkimPrivateKeyPath,
        String dkimPrivateKeySecretRef,
        Boolean clearDkimPrivateKeySecretRef,
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
}
