package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.mailsecurity.ProcessingResult;

public record MailErrorClassification(
        MailProcessingErrorType errorType,
        String detail,
        MailProcessingContext context,
        MailRecordDisposition recordDisposition,
        boolean retryable,
        ProcessingResult processingResult,
        String auditAction,
        MailErrorTarget target,
        String quarantineReason,
        String quarantineDetail
) {
}
