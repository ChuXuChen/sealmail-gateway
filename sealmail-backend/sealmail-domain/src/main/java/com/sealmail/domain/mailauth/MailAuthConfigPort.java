package com.sealmail.domain.mailauth;

import java.time.Instant;
import java.util.List;

public interface MailAuthConfigPort {

    MailAuthSettings getSettings();

    MailAuthSettings updateSettings(MailAuthSettingsUpdate update);

    List<DnsRecordSettings> dnsRecordSettings(String domain);

    record MailAuthSettings(
            boolean enabled,
            String authservId,
            boolean skipPrivateRelay,
            boolean dkimEnabled,
            String dkimSelector,
            String dkimPrivateKeyPath,
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
        public MailAuthSettings {
            dkimSignedHeaders = dkimSignedHeaders == null ? List.of() : List.copyOf(dkimSignedHeaders);
            spfIp4 = spfIp4 == null ? List.of() : List.copyOf(spfIp4);
            spfIp6 = spfIp6 == null ? List.of() : List.copyOf(spfIp6);
            spfIncludes = spfIncludes == null ? List.of() : List.copyOf(spfIncludes);
        }
    }

    record MailAuthSettingsUpdate(
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
        public MailAuthSettingsUpdate {
            dkimSignedHeaders = dkimSignedHeaders == null ? null : List.copyOf(dkimSignedHeaders);
            spfIp4 = spfIp4 == null ? null : List.copyOf(spfIp4);
            spfIp6 = spfIp6 == null ? null : List.copyOf(spfIp6);
            spfIncludes = spfIncludes == null ? null : List.copyOf(spfIncludes);
        }
    }

    record DnsRecordSettings(
            String type,
            String name,
            String value,
            boolean available
    ) {
    }
}
