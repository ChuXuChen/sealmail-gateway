package com.sealmail.infra.mailauth;

import com.google.common.net.InternetDomainName;

import java.util.Locale;

final class PublicSuffixOrganizationalDomainResolver {

    String organizationalDomain(String domain) {
        String canonical = canonicalDomain(domain);
        if (canonical == null) {
            return null;
        }
        try {
            InternetDomainName name = InternetDomainName.from(canonical);
            if (!name.isUnderPublicSuffix()) {
                return null;
            }
            return name.topPrivateDomain().toString();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return null;
        }
    }

    boolean relaxedAligned(String fromDomain, String authDomain) {
        String from = canonicalDomain(fromDomain);
        String auth = canonicalDomain(authDomain);
        if (from == null || auth == null) {
            return false;
        }
        if (from.equals(auth)) {
            return true;
        }
        String fromOrg = organizationalDomain(from);
        String authOrg = organizationalDomain(auth);
        return fromOrg != null && fromOrg.equals(authOrg);
    }

    String canonicalDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        String canonical = domain.trim().toLowerCase(Locale.ROOT);
        while (canonical.endsWith(".")) {
            canonical = canonical.substring(0, canonical.length() - 1);
        }
        return canonical.isBlank() ? null : canonical;
    }
}
