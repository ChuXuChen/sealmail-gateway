package com.sealmail.domain.dlp.spi;

import com.sealmail.domain.dlp.DlpContentBundle;
import com.sealmail.domain.dlp.DlpPolicyResolution;
import com.sealmail.domain.mailsecurity.MailProcessingContext;

public interface DlpPolicyResolver {

    DlpPolicyResolution resolve(MailProcessingContext context, DlpContentBundle content);

    DlpPolicyResolution resolvePolicy(String policyId, MailProcessingContext context, DlpContentBundle content);
}
