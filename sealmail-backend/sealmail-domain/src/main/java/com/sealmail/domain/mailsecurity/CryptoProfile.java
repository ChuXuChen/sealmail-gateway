package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.PreferredAlgorithm;

import java.util.Locale;
import java.util.Optional;

public enum CryptoProfile {
    AUTO,
    STANDARD,
    GM;

    public static CryptoProfile fromPreferredAlgorithm(PreferredAlgorithm preferredAlgorithm) {
        if (preferredAlgorithm == null || preferredAlgorithm == PreferredAlgorithm.AUTO) {
            return AUTO;
        }
        return switch (preferredAlgorithm) {
            case STANDARD_ONLY -> STANDARD;
            case GM_ONLY -> GM;
            case AUTO -> AUTO;
        };
    }

    public static CryptoProfile fromDomainConfig(DomainConfig domainConfig) {
        return domainConfig == null ? AUTO : fromPreferredAlgorithm(domainConfig.getPreferredAlgorithm());
    }

    public boolean isConcrete() {
        return this == STANDARD || this == GM;
    }

    public static Optional<CryptoProfile> fromCertificateAlgorithm(String algorithm) {
        String normalized = normalizeAlgorithm(algorithm);
        return switch (normalized) {
            case "RSA" -> Optional.of(STANDARD);
            case "SM2", "EC", "ECDSA" -> Optional.of(GM);
            default -> Optional.empty();
        };
    }

    private static String normalizeAlgorithm(String algorithm) {
        return algorithm == null ? "UNKNOWN" : algorithm.trim().toUpperCase(Locale.ROOT);
    }
}
