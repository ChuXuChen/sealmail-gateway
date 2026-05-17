package com.sealmail.infra.mailauth;

import com.sealmail.domain.mailauth.DnsProbeStatus;
import com.sealmail.domain.mailauth.DnsRecord;
import com.sealmail.infra.dns.DnsTxtResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DnsLookupMailAuthProbeTest {

    @Test
    void classifiesMatchedMismatchedAndMissingTxtRecords() {
        DnsLookupMailAuthProbe probe = new DnsLookupMailAuthProbe(new FakeDnsTxtResolver(Map.of(
                "example.com", List.of("v=spf1 mx ~all"),
                "_dmarc.example.com", List.of("v=DMARC1; p=none")
        )));

        var results = probe.probe("example.com", List.of(
                new DnsRecord("SPF", "example.com", "v=spf1 mx ~all", true),
                new DnsRecord("DMARC", "_dmarc.example.com", "v=DMARC1; p=reject", true),
                new DnsRecord("DKIM", "sealmail._domainkey.example.com", "v=DKIM1; k=rsa; p=abc", true)
        ));

        assertEquals(DnsProbeStatus.MATCH, results.get(0).status());
        assertEquals(DnsProbeStatus.MISMATCH, results.get(1).status());
        assertEquals(DnsProbeStatus.NOT_FOUND, results.get(2).status());
    }

    private static final class FakeDnsTxtResolver extends DnsTxtResolver {
        private final Map<String, List<String>> txt;

        private FakeDnsTxtResolver(Map<String, List<String>> txt) {
            this.txt = txt;
        }

        @Override
        public List<String> txt(String name) {
            return txt.getOrDefault(name, List.of());
        }
    }
}
