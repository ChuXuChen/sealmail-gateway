package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.policy.PreferredAlgorithm;

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
}
