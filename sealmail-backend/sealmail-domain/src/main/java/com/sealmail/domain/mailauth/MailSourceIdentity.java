package com.sealmail.domain.mailauth;

import com.sealmail.domain.policy.DomainName;

public record MailSourceIdentity(
        String sourceIp,
        String envelopeFromDomain,
        String headerFromDomain,
        String helo,
        boolean trustedProxyOverride,
        String resolutionDetail
) {

    public MailSourceIdentity {
        sourceIp = sourceIp != null && !sourceIp.isBlank() ? sourceIp.trim() : null;
        envelopeFromDomain = normalizeDomain(envelopeFromDomain);
        headerFromDomain = normalizeDomain(headerFromDomain);
        helo = helo != null && !helo.isBlank() ? helo.trim() : null;
        resolutionDetail = resolutionDetail != null && !resolutionDetail.isBlank() ? resolutionDetail.trim() : null;
    }

    private static String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        return DomainName.normalize(domain);
    }
}
