package com.sealmail.infra.mailauth;

import com.sealmail.domain.mailauth.AuthenticationMechanismResult;
import com.sealmail.domain.mailauth.AuthenticationResult;
import com.sealmail.domain.mailauth.DmarcPolicyMode;
import com.sealmail.infra.config.properties.MailAuthProperties;
import com.sealmail.infra.dns.DnsTxtResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DmarcEvaluator {

    private final DnsTxtResolver dns;
    private final MailAuthProperties properties;
    private final PublicSuffixOrganizationalDomainResolver domainResolver =
            new PublicSuffixOrganizationalDomainResolver();

    DmarcEvaluator(DnsTxtResolver dns, MailAuthProperties properties) {
        this.dns = dns;
        this.properties = properties;
    }

    DmarcEvaluation verify(String fromDomain,
                           String mailFromDomain,
                           List<AuthenticationMechanismResult> dkimResults,
                           AuthenticationMechanismResult spfResult) {
        if (!properties.getDmarc().isEnabled()) {
            return DmarcEvaluation.none(fromDomain, "DMARC disabled");
        }
        if (isBlank(fromDomain)) {
            return DmarcEvaluation.none(fromDomain, "Header From domain missing");
        }
        DmarcRecord record = dmarcRecord(fromDomain);
        if (record == null) {
            return DmarcEvaluation.none(fromDomain, "DMARC record not found");
        }
        boolean spfAligned = spfResult != null
                && spfResult.result() == AuthenticationResult.PASS
                && aligned(fromDomain, mailFromDomain, record.tags().getOrDefault("aspf", "r"));
        boolean dkimAligned = dkimResults != null && dkimResults.stream()
                .anyMatch(result -> result.result() == AuthenticationResult.PASS
                        && aligned(fromDomain, result.domain(), record.tags().getOrDefault("adkim", "r")));
        AuthenticationResult result = spfAligned || dkimAligned ? AuthenticationResult.PASS : AuthenticationResult.FAIL;
        return new DmarcEvaluation(
                result,
                effectivePolicy(record),
                fromDomain,
                "DMARC evaluated");
    }

    private DmarcRecord dmarcRecord(String fromDomain) {
        return domainResolver.canonicalDomainValue(fromDomain)
                .flatMap(this::dmarcRecordFor)
                .orElse(null);
    }

    private java.util.Optional<DmarcRecord> dmarcRecordFor(String canonicalFrom) {
        Map<String, String> exact = recordAt("_dmarc." + canonicalFrom);
        if (!exact.isEmpty()) {
            return java.util.Optional.of(new DmarcRecord(canonicalFrom, exact, false));
        }
        return domainResolver.organizationalDomainValue(canonicalFrom)
                .filter(orgDomain -> !canonicalFrom.equals(orgDomain))
                .map(orgDomain -> new DmarcRecord(orgDomain, recordAt("_dmarc." + orgDomain), true))
                .filter(record -> !record.tags().isEmpty());
    }

    private Map<String, String> recordAt(String name) {
        String record = dns.txt(name).stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith("v=dmarc1"))
                .findFirst()
                .orElse(null);
        if (record == null) {
            return Map.of();
        }
        Map<String, String> tags = new HashMap<>();
        for (String part : record.split(";")) {
            int equals = part.indexOf('=');
            if (equals > 0) {
                tags.put(part.substring(0, equals).trim().toLowerCase(Locale.ROOT),
                        part.substring(equals + 1).trim().toLowerCase(Locale.ROOT));
            }
        }
        return tags;
    }

    private DmarcPolicyMode effectivePolicy(DmarcRecord record) {
        if (record.organizationalFallback() && record.tags().containsKey("sp")) {
            return DmarcPolicyMode.fromTag(record.tags().get("sp"));
        }
        return DmarcPolicyMode.fromTag(record.tags().getOrDefault("p", "none"));
    }

    private boolean aligned(String fromDomain, String authDomain, String mode) {
        java.util.Optional<String> from = domainResolver.canonicalDomainValue(fromDomain);
        java.util.Optional<String> auth = domainResolver.canonicalDomainValue(authDomain);
        if (from.isEmpty() || auth.isEmpty()) {
            return false;
        }
        if ("s".equals(mode)) {
            return from.equals(auth);
        }
        return domainResolver.relaxedAligned(from.get(), auth.get());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    record DmarcEvaluation(AuthenticationResult result,
                           DmarcPolicyMode policy,
                           String domain,
                           String detail) {

        static DmarcEvaluation none(String domain, String detail) {
            return new DmarcEvaluation(AuthenticationResult.NONE, DmarcPolicyMode.NONE, domain, detail);
        }
    }

    private record DmarcRecord(String domain,
                               Map<String, String> tags,
                               boolean organizationalFallback) {
    }
}
