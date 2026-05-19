package com.sealmail.infra.mailauth;

import com.google.common.net.InternetDomainName;

import java.util.Locale;
import java.util.Optional;

final class PublicSuffixOrganizationalDomainResolver {

    String organizationalDomain(String domain) {
        return organizationalDomainValue(domain).orElse(null);
    }

    Optional<String> organizationalDomainValue(String domain) {
        Optional<String> canonical = canonicalDomainValue(domain);
        if (canonical.isEmpty()) {
            return Optional.empty();
        }
        try {
            InternetDomainName name = InternetDomainName.from(canonical.get());
            if (!name.isUnderPublicSuffix()) {
                return Optional.empty();
            }
            return Optional.of(name.topPrivateDomain().toString());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Optional.empty();
        }
    }

    boolean relaxedAligned(String fromDomain, String authDomain) {
        Optional<String> from = canonicalDomainValue(fromDomain);
        Optional<String> auth = canonicalDomainValue(authDomain);
        if (from.isEmpty() || auth.isEmpty()) {
            return false;
        }
        if (from.equals(auth)) {
            return true;
        }
        Optional<String> fromOrg = organizationalDomainValue(from.get());
        Optional<String> authOrg = organizationalDomainValue(auth.get());
        return fromOrg.isPresent() && fromOrg.equals(authOrg);
    }

    String canonicalDomain(String domain) {
        return canonicalDomainValue(domain).orElse(null);
    }

    Optional<String> canonicalDomainValue(String domain) {
        if (domain == null || domain.isBlank()) {
            return Optional.empty();
        }
        String canonical = domain.trim().toLowerCase(Locale.ROOT);
        while (canonical.endsWith(".")) {
            canonical = canonical.substring(0, canonical.length() - 1);
        }
        return canonical.isBlank() ? Optional.empty() : Optional.of(canonical);
    }
}
