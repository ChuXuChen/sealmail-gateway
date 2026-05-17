package com.sealmail.domain.dlp.spi;

import com.sealmail.domain.dlp.DlpContentBundle;
import com.sealmail.domain.mailsecurity.MailProcessingContext;

public interface DlpContentExtractor {

    DlpContentBundle extract(byte[] rawMail, MailProcessingContext context);
}
