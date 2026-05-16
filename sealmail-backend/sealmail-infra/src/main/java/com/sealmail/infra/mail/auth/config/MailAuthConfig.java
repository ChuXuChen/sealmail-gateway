package com.sealmail.infra.mail.auth.config;

import java.time.Instant;
import java.util.List;

public record MailAuthConfig(
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
}
