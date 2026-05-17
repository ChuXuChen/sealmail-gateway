package com.sealmail.domain.mailauth;

import java.util.List;
import java.util.Optional;

public interface MailAuthPolicyRepository {

    MailAuthPolicy findPolicy();

    MailAuthPolicy savePolicy(MailAuthPolicy policy);

    Optional<DomainMailAuthPolicy> findDomainPolicy(String domainName);

    DomainMailAuthPolicy saveDomainPolicy(DomainMailAuthPolicy policy);

    List<DomainMailAuthPolicy> findDomainPolicies();

    DnsProbeResult saveDnsProbeResult(DnsProbeResult result);

    List<DnsProbeResult> findLatestDnsProbeResults(String domainName);
}
