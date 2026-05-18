package com.sealmail.domain.config;

import java.util.List;

public record GmTlsPolicy(
        List<String> protocols,
        List<String> cipherSuites,
        boolean trustAll
) {
    public GmTlsPolicy {
        protocols = protocols == null ? List.of("TLCPv1.1", "TLCP", "TLSv1.3") : List.copyOf(protocols);
        cipherSuites = cipherSuites == null ? List.of("TLS_SM4_GCM_SM3", "TLS_SM4_CCM_SM3") : List.copyOf(cipherSuites);
    }

    public static GmTlsPolicy defaults() {
        return new GmTlsPolicy(
                List.of("TLCPv1.1", "TLCP", "TLSv1.3"),
                List.of("TLS_SM4_GCM_SM3", "TLS_SM4_CCM_SM3"),
                false);
    }
}
