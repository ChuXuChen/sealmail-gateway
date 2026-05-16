package com.sealmail.app.dto.response;

public record MailAuthStatusResponse(
        boolean enabled,
        String authservId,
        boolean dkimEnabled,
        String dkimSelector,
        boolean spfEnabled,
        boolean dmarcEnabled,
        boolean dmarcQuarantineRejectPolicy,
        boolean skipPrivateRelay
) {
}
