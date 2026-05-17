package com.sealmail.infra.mailauth;

import com.sealmail.domain.mailauth.DnsProbeResult;
import com.sealmail.domain.mailauth.DnsProbeStatus;
import com.sealmail.domain.mailauth.DnsRecord;
import com.sealmail.domain.mailauth.MailAuthDnsProbePort;
import com.sealmail.infra.dns.DnsTxtResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Component
public class DnsLookupMailAuthProbe implements MailAuthDnsProbePort {

    private final DnsTxtResolver dnsTxtResolver;

    public DnsLookupMailAuthProbe(DnsTxtResolver dnsTxtResolver) {
        this.dnsTxtResolver = dnsTxtResolver;
    }

    @Override
    public List<DnsProbeResult> probe(String domainName, List<DnsRecord> expectedRecords) {
        return expectedRecords.stream()
                .map(record -> probeRecord(domainName, record))
                .toList();
    }

    private DnsProbeResult probeRecord(String domainName, DnsRecord record) {
        try {
            if (!record.available()) {
                return result(domainName, record, "", DnsProbeStatus.NOT_FOUND, "Expected record is unavailable");
            }
            List<String> observed = dnsTxtResolver.txt(record.name());
            if (observed.isEmpty()) {
                return result(domainName, record, "", DnsProbeStatus.NOT_FOUND, "TXT record not found");
            }
            boolean matched = observed.stream().anyMatch(value -> normalize(value).equals(normalize(record.value())));
            return result(
                    domainName,
                    record,
                    String.join("\n", observed),
                    matched ? DnsProbeStatus.MATCH : DnsProbeStatus.MISMATCH,
                    matched ? "TXT record matches expected value" : "TXT record differs from expected value");
        } catch (Exception e) {
            return result(domainName, record, "", DnsProbeStatus.TEMPERROR, e.getMessage());
        }
    }

    private DnsProbeResult result(String domainName,
                                  DnsRecord record,
                                  String observed,
                                  DnsProbeStatus status,
                                  String detail) {
        return new DnsProbeResult(
                domainName,
                record.type(),
                record.name(),
                hash(record.value()),
                observed,
                status,
                detail,
                Instant.now());
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value != null ? value : "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "";
        }
    }
}
