package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.dlp.DlpContentBundle;
import com.sealmail.domain.mailsecurity.AttachmentDescriptor;
import com.sealmail.domain.mailsecurity.AttachmentSecurityAction;
import com.sealmail.domain.mailsecurity.AttachmentSecurityResult;
import com.sealmail.domain.mailsecurity.MailInspectionBundle;
import com.sealmail.domain.mailsecurity.MailProcessingStatusSnapshot;
import com.sealmail.infra.config.properties.AttachmentSecurityProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentSecurityServiceTest {

    @Test
    void flagsHighRiskDoubleExtensionAndMimeMismatch() {
        AttachmentSecurityService service = new AttachmentSecurityService(new AttachmentSecurityProperties());
        AttachmentSecurityResult result = service.evaluate(bundle(descriptor(
                "invoice.pdf.exe",
                "application/pdf",
                "application/x-msdownload",
                "exe",
                512L)));

        assertEquals(AttachmentSecurityAction.QUARANTINE, result.action());
        assertEquals(MailProcessingStatusSnapshot.QUARANTINED, result.status());
        assertTrue(result.findings().stream().anyMatch(finding -> "HIGH_RISK_EXTENSION".equals(finding.code())));
        assertTrue(result.findings().stream().anyMatch(finding -> "DOUBLE_EXTENSION".equals(finding.code())));
        assertTrue(result.findings().stream().anyMatch(finding -> "MIME_MISMATCH".equals(finding.code())));
    }

    @Test
    void honorsConfiguredTotalSizeLimitAction() {
        AttachmentSecurityProperties properties = new AttachmentSecurityProperties();
        properties.setMaxTotalAttachmentBytes(10);
        properties.setTotalSizeLimitAction("WARN");
        AttachmentSecurityService service = new AttachmentSecurityService(properties);

        AttachmentSecurityResult result = service.evaluate(bundle(
                descriptor("a.txt", "text/plain", "text/plain", "txt", 8L),
                descriptor("b.txt", "text/plain", "text/plain", "txt", 8L)));

        assertEquals(AttachmentSecurityAction.WARN, result.action());
        assertEquals(MailProcessingStatusSnapshot.PASS, result.status());
        assertTrue(result.findings().stream()
                .anyMatch(finding -> "TOTAL_ATTACHMENT_SIZE_LIMIT_EXCEEDED".equals(finding.code())));
    }

    private static MailInspectionBundle bundle(AttachmentDescriptor... descriptors) {
        return new MailInspectionBundle(
                new DlpContentBundle(List.of(), List.of()),
                List.of(descriptors),
                List.of());
    }

    private static AttachmentDescriptor descriptor(String fileName,
                                                    String declaredMime,
                                                    String detectedMime,
                                                    String extension,
                                                    long size) {
        return new AttachmentDescriptor(
                fileName,
                fileName,
                declaredMime,
                detectedMime,
                extension,
                size,
                "sha256",
                false,
                false,
                false,
                null,
                null,
                List.of(fileName),
                List.of());
    }
}
