package com.sealmail.domain.mailauth;

import java.util.List;

public interface MailAuthDnsProbePort {

    List<DnsProbeResult> probe(String domainName, List<DnsRecord> expectedRecords);
}
