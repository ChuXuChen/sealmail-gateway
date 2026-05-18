package com.sealmail.domain.config;

import java.util.List;

public record StandardTlsPolicy(
        List<String> protocols,
        List<String> cipherSuites,
        boolean verifyPeerCertificate
) {
    public StandardTlsPolicy {
        protocols = protocols == null ? List.of("TLSv1.3", "TLSv1.2") : List.copyOf(protocols);
        cipherSuites = cipherSuites == null ? List.of() : List.copyOf(cipherSuites);
    }

    public static StandardTlsPolicy defaults() {
        return new StandardTlsPolicy(List.of("TLSv1.3", "TLSv1.2"), List.of(), true);
    }
}
