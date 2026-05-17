package com.sealmail.domain.mailauth;

import com.sealmail.domain.policy.DomainName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record DomainMailAuthPolicy(
        String domainName,
        boolean enabled,
        DkimSigningPolicy dkimSigningPolicy,
        SpfPublicationPolicy spfPublicationPolicy,
        DmarcPublicationPolicy dmarcPublicationPolicy,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    public DomainMailAuthPolicy {
        domainName = DomainName.requireValid(domainName);
        dkimSigningPolicy = dkimSigningPolicy != null ? dkimSigningPolicy : DkimSigningPolicy.disabled();
        spfPublicationPolicy = spfPublicationPolicy != null ? spfPublicationPolicy : SpfPublicationPolicy.disabled();
        dmarcPublicationPolicy = dmarcPublicationPolicy != null
                ? dmarcPublicationPolicy
                : DmarcPublicationPolicy.disabled();
        Instant now = Instant.now();
        createdAt = createdAt != null ? createdAt : now;
        updatedAt = updatedAt != null ? updatedAt : createdAt;
    }

    public static DomainMailAuthPolicy defaults(String domainName) {
        return new DomainMailAuthPolicy(
                domainName,
                true,
                DkimSigningPolicy.disabled(),
                new SpfPublicationPolicy(true, true, true, List.of(), List.of(), List.of(), "~all"),
                new DmarcPublicationPolicy(true, DmarcPolicyMode.NONE, DmarcPolicyMode.NONE,
                        DmarcAlignmentMode.RELAXED, DmarcAlignmentMode.RELAXED, 100, null, null),
                null,
                null,
                0);
    }

    public List<DnsRecord> dnsRecords(Optional<String> dkimPublicKeyData) {
        List<DnsRecord> records = new ArrayList<>();
        String selector = dkimSigningPolicy.selector().value();
        String dkimName = selector + "._domainkey." + domainName;
        boolean dkimAvailable = enabled && dkimSigningPolicy.enabled()
                && dkimPublicKeyData.isPresent()
                && !dkimPublicKeyData.get().isBlank();
        records.add(new DnsRecord(
                "DKIM",
                dkimName,
                dkimAvailable ? "v=DKIM1; k=rsa; p=" + dkimPublicKeyData.get() : "",
                dkimAvailable));

        records.add(new DnsRecord(
                "SPF",
                domainName,
                enabled && spfPublicationPolicy.enabled() ? spfPublicationPolicy.txtValue() : "",
                enabled && spfPublicationPolicy.enabled()));

        records.add(new DnsRecord(
                "DMARC",
                "_dmarc." + domainName,
                enabled && dmarcPublicationPolicy.enabled() ? dmarcPublicationPolicy.txtValue() : "",
                enabled && dmarcPublicationPolicy.enabled()));
        return List.copyOf(records);
    }

    public DomainMailAuthPolicy withDkimSigningPolicy(DkimSigningPolicy value) {
        return new DomainMailAuthPolicy(domainName, enabled, value, spfPublicationPolicy, dmarcPublicationPolicy,
                createdAt, Instant.now(), version);
    }
}
