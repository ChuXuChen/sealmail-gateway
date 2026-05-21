package com.sealmail.infra.dlp;

import com.sealmail.domain.dlp.DlpContentBundle;
import com.sealmail.domain.dlp.spi.DlpContentExtractor;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.infra.mail.inspection.MailInspectionService;
import org.springframework.stereotype.Component;

@Component
public class MimeContentExtractor implements DlpContentExtractor {

    private final MailInspectionService mailInspectionService;

    public MimeContentExtractor(MailInspectionService mailInspectionService) {
        this.mailInspectionService = mailInspectionService;
    }

    @Override
    public DlpContentBundle extract(byte[] rawMail, MailProcessingContext context) {
        return mailInspectionService.inspect(rawMail, context).dlpContent();
    }

    public ExtractedContent extract(byte[] rawContent) {
        MailInspectionService.ExtractedContent extracted = mailInspectionService.extractContent(rawContent);
        return new ExtractedContent(extracted.subject(), extracted.body());
    }

    public record ExtractedContent(String subject, String body) {
    }
}
