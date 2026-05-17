package com.sealmail.domain.dlp.spi;

import com.sealmail.domain.dlp.DlpEvaluationResult;
import com.sealmail.domain.dlp.DlpTestRequest;
import com.sealmail.domain.mailsecurity.MailProcessingContext;

public interface DlpEvaluationPort {

    DlpEvaluationResult evaluate(byte[] rawMail, MailProcessingContext context, boolean persistEvent);

    DlpEvaluationResult test(DlpTestRequest request);

    DlpEvaluationResult simulatePolicy(String policyId, DlpTestRequest request);
}
