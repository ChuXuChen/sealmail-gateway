package com.sealmail.infra.mail.inspection;

import com.sealmail.domain.dlp.DlpContentKind;
import com.sealmail.domain.mailsecurity.AttachmentDescriptor;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailInspectionBundle;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.config.properties.AttachmentSecurityProperties;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailInspectionServiceTest {

    @Test
    void extractsZipEntryTextAndAttachmentDescriptors() throws Exception {
        MailInspectionService service = new MailInspectionService(new AttachmentSecurityProperties());
        byte[] rawMail = multipartMail(
                "hello body",
                zipBytes("note.txt", "zip secret"),
                "archive.zip",
                "application/zip");

        MailInspectionBundle bundle = service.inspect(rawMail, context(rawMail));

        assertTrue(bundle.dlpContent().parts().stream()
                .anyMatch(part -> part.kind() == DlpContentKind.BODY_TEXT
                        && part.text().contains("hello body")));
        assertTrue(bundle.dlpContent().parts().stream()
                .anyMatch(part -> part.kind() == DlpContentKind.ATTACHMENT_ZIP_ENTRY
                        && part.text().contains("zip secret")));
        assertEquals(2, bundle.attachments().size());
        assertTrue(bundle.attachments().stream().anyMatch(AttachmentDescriptor::archive));
    }

    @Test
    void truncatesOversizedAttachmentsAndReportsWarnings() throws Exception {
        AttachmentSecurityProperties properties = new AttachmentSecurityProperties();
        properties.setMaxAttachmentBytes(16);
        MailInspectionService service = new MailInspectionService(properties);
        byte[] rawMail = multipartMail(
                "hello body",
                "abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8),
                "notes.txt",
                "text/plain");

        MailInspectionBundle bundle = service.inspect(rawMail, context(rawMail));

        AttachmentDescriptor attachment = bundle.attachments().stream()
                .filter(item -> "notes.txt".equals(item.fileName()))
                .findFirst()
                .orElseThrow();

        assertTrue(attachment.truncated());
        assertTrue(attachment.warnings().stream().anyMatch(warning -> warning.contains("Attachment truncated")));
    }

    private static MailProcessingContext context(byte[] rawMail) {
        MailEnvelope envelope = new MailEnvelope(
                "msg-1@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                "helo",
                Instant.now(),
                rawMail);
        return MailProcessingContext.create(envelope)
                .withDirection(MailDirection.OUTBOUND)
                .withSubject("Attachment inspection test");
    }

    private static byte[] multipartMail(String body,
                                        byte[] attachmentBytes,
                                        String fileName,
                                        String contentType) throws Exception {
        String boundary = "sealmail-test-boundary";
        String encodedAttachment = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(attachmentBytes);
        String raw = String.join("\r\n",
                "From: sender@example.com",
                "To: recipient@example.com",
                "Subject: Attachment inspection test",
                "MIME-Version: 1.0",
                "Content-Type: multipart/mixed; boundary=\"" + boundary + "\"",
                "",
                "--" + boundary,
                "Content-Type: text/plain; charset=UTF-8",
                "Content-Transfer-Encoding: 7bit",
                "",
                body,
                "--" + boundary,
                "Content-Type: " + contentType + "; name=\"" + fileName + "\"",
                "Content-Transfer-Encoding: base64",
                "Content-Disposition: attachment; filename=\"" + fileName + "\"",
                "",
                encodedAttachment,
                "--" + boundary + "--",
                "");
        return raw.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] zipBytes(String entryName, String content) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return out.toByteArray();
        }
    }
}
