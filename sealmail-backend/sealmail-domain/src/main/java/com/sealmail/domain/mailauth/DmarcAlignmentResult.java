package com.sealmail.domain.mailauth;

public record DmarcAlignmentResult(
        boolean spfAligned,
        boolean dkimAligned,
        String fromDomain,
        String spfDomain,
        String dkimDomain
) {

    public boolean passed() {
        return spfAligned || dkimAligned;
    }
}
