package com.sealmail.domain.mailauth;

import java.util.ArrayList;
import java.util.List;

public record DmarcPublicationPolicy(
        boolean enabled,
        DmarcPolicyMode policy,
        DmarcPolicyMode subdomainPolicy,
        DmarcAlignmentMode dkimAlignment,
        DmarcAlignmentMode spfAlignment,
        int pct,
        String rua,
        String ruf
) {

    public DmarcPublicationPolicy {
        policy = policy != null ? policy : DmarcPolicyMode.NONE;
        subdomainPolicy = subdomainPolicy != null ? subdomainPolicy : policy;
        dkimAlignment = dkimAlignment != null ? dkimAlignment : DmarcAlignmentMode.RELAXED;
        spfAlignment = spfAlignment != null ? spfAlignment : DmarcAlignmentMode.RELAXED;
        if (pct < 0 || pct > 100) {
            throw new IllegalArgumentException("DMARC pct must be between 0 and 100");
        }
        rua = blankToNull(rua);
        ruf = blankToNull(ruf);
    }

    public static DmarcPublicationPolicy disabled() {
        return new DmarcPublicationPolicy(false, DmarcPolicyMode.NONE, DmarcPolicyMode.NONE,
                DmarcAlignmentMode.RELAXED, DmarcAlignmentMode.RELAXED, 100, null, null);
    }

    public String txtValue() {
        List<String> parts = new ArrayList<>();
        parts.add("v=DMARC1");
        parts.add("p=" + policy.tagValue());
        if (subdomainPolicy != policy) {
            parts.add("sp=" + subdomainPolicy.tagValue());
        }
        parts.add("adkim=" + dkimAlignment.tagValue());
        parts.add("aspf=" + spfAlignment.tagValue());
        parts.add("pct=" + pct);
        if (rua != null) {
            parts.add("rua=" + rua);
        }
        if (ruf != null) {
            parts.add("ruf=" + ruf);
        }
        return String.join("; ", parts);
    }

    private static String blankToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
