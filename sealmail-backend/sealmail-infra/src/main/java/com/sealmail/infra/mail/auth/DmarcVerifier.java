package com.sealmail.infra.mail.auth;

import com.sealmail.infra.config.properties.MailAuthProperties;
import com.sealmail.infra.dns.DnsTxtResolver;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DmarcVerifier {

    private final DnsTxtResolver dns;
    private final MailAuthProperties properties;

    public DmarcVerifier(DnsTxtResolver dns, MailAuthProperties properties) {
        this.dns = dns;
        this.properties = properties;
    }

    public DmarcDecision verify(String fromDomain, String mailFromDomain, String dkimDomain,
                                AuthResult spfResult, AuthResult dkimResult) {
        if (!properties.getDmarc().isEnabled() || fromDomain == null || fromDomain.isBlank()) {
            return new DmarcDecision(AuthResult.NONE, DmarcPolicy.NONE);
        }
        Map<String, String> record = dmarcRecord(fromDomain);
        if (record.isEmpty()) {
            return new DmarcDecision(AuthResult.NONE, DmarcPolicy.NONE);
        }

        boolean spfAligned = spfResult == AuthResult.PASS
                && aligned(fromDomain, mailFromDomain, record.getOrDefault("aspf", "r"));
        boolean dkimAligned = dkimResult == AuthResult.PASS
                && aligned(fromDomain, dkimDomain, record.getOrDefault("adkim", "r"));
        DmarcPolicy policy = policy(record.getOrDefault("p", "none"));
        AuthResult result = (spfAligned || dkimAligned) ? AuthResult.PASS : AuthResult.FAIL;
        return new DmarcDecision(result, policy);
    }

    private Map<String, String> dmarcRecord(String fromDomain) {
        String record = dns.txt("_dmarc." + fromDomain).stream()
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

    private DmarcPolicy policy(String value) {
        return switch (value) {
            case "reject" -> DmarcPolicy.REJECT;
            case "quarantine" -> DmarcPolicy.QUARANTINE;
            default -> DmarcPolicy.NONE;
        };
    }

    private boolean aligned(String fromDomain, String authDomain, String mode) {
        if (fromDomain == null || authDomain == null) {
            return false;
        }
        String from = fromDomain.toLowerCase(Locale.ROOT);
        String auth = authDomain.toLowerCase(Locale.ROOT);
        if ("s".equals(mode)) {
            return from.equals(auth);
        }
        return from.equals(auth) || auth.endsWith("." + from) || from.endsWith("." + auth);
    }

    public record DmarcDecision(AuthResult result, DmarcPolicy policy) {
    }
}
