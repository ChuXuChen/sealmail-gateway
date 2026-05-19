package com.sealmail.infra.dlp;

import com.sealmail.domain.dlp.DlpContentBundle;
import com.sealmail.domain.dlp.DlpContentKind;
import com.sealmail.domain.dlp.DlpContentPart;
import com.sealmail.domain.dlp.spi.DlpContentExtractor;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MIME 内容提取器
 * 从原始邮件字节数据中提取主题和正文用于 DLP 扫描
 */
@Slf4j
@Component
public class MimeContentExtractor implements DlpContentExtractor {

    private static final int MAX_PART_CHARS = 512_000;
    private static final int MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 20;
    private static final int MAX_ZIP_TOTAL_BYTES = 10 * 1024 * 1024;

    private final Session mailSession;

    public MimeContentExtractor() {
        this.mailSession = Session.getInstance(new Properties());
    }

    @Override
    public DlpContentBundle extract(byte[] rawMail, MailProcessingContext context) {
        byte[] rawContent = rawMail != null ? rawMail : new byte[0];
        List<DlpContentPart> parts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try (ByteArrayInputStream is = new ByteArrayInputStream(rawContent)) {
            MimeMessage msg = new MimeMessage(mailSession, is);
            String subject = msg.getSubject();
            if (subject == null || subject.isBlank()) {
                subject = context != null ? context.subject() : "";
            }
            parts.add(part("subject", DlpContentKind.SUBJECT, null, "text/plain", subject, false, List.of()));
            parts.add(part("headers", DlpContentKind.HEADERS, null, "message/rfc822-headers", collectHeaders(msg), false, List.of()));
            extractPart(msg, "mime", parts, warnings, 0);
        } catch (Exception e) {
            log.warn("Failed to extract MIME part bundle: {}", e.getMessage());
            warnings.add("MIME extraction failed: " + e.getMessage());
            ExtractedContent fallback = fallbackExtract(rawContent);
            parts.add(part("subject", DlpContentKind.SUBJECT, null, "text/plain", fallback.subject(), false, List.of()));
            parts.add(part("body", DlpContentKind.BODY_TEXT, null, "text/plain", fallback.body(), fallback.body().length() > MAX_PART_CHARS, warnings));
        }
        return new DlpContentBundle(parts, warnings);
    }

    public ExtractedContent extract(byte[] rawContent) {
        try (ByteArrayInputStream is = new ByteArrayInputStream(rawContent)) {
            MimeMessage msg = new MimeMessage(mailSession, is);

            String subject = msg.getSubject();
            String body = extractBody(msg.getContent());

            return new ExtractedContent(subject, body);
        } catch (Exception e) {
            log.warn("Failed to extract MIME content: {}", e.getMessage());
            return fallbackExtract(rawContent);
        }
    }

    private String extractBody(Object content) {
        if (content == null) {
            return "";
        }

        if (content instanceof String) {
            return (String) content;
        }

        if (content instanceof Multipart) {
            return extractMultipartBody((Multipart) content);
        }

        return content.toString();
    }

    private String extractMultipartBody(Multipart multipart) {
        StringBuilder sb = new StringBuilder();
        try {
            int count = multipart.getCount();
            for (int i = 0; i < count; i++) {
                BodyPart part = multipart.getBodyPart(i);
                Object content = part.getContent();
                if (content instanceof String) {
                    sb.append(content).append(" ");
                } else if (content instanceof Multipart) {
                    sb.append(extractMultipartBody((Multipart) content));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract multipart content: {}", e.getMessage());
        }
        return sb.toString();
    }

    private void extractPart(Part mailPart,
                             String partId,
                             List<DlpContentPart> parts,
                             List<String> warnings,
                             int depth) {
        if (depth > 8) {
            warnings.add("MIME nesting depth exceeded at " + partId);
            return;
        }
        try {
            String rawContentType = mailPart.getContentType();
            String contentType = safeContentType(rawContentType);
            String lowerType = contentType.toLowerCase(Locale.ROOT);
            String fileName = mailPart.getFileName();

            if (mailPart.isMimeType("multipart/*")) {
                Object content = mailPart.getContent();
                if (!(content instanceof Multipart multipart)) {
                    warnings.add("MIME multipart content was not readable at " + partId);
                    return;
                }
                int count = multipart.getCount();
                for (int i = 0; i < count; i++) {
                    extractPart(multipart.getBodyPart(i), partId + "." + i, parts, warnings, depth + 1);
                }
                return;
            }

            if (isInlineTextPart(lowerType)) {
                byte[] bytes = readPartBytes(mailPart, warnings, partId);
                DlpContentKind kind = lowerType.contains("html")
                        ? DlpContentKind.BODY_HTML
                        : DlpContentKind.BODY_TEXT;
                String text = decodeText(bytes, rawContentType);
                String extracted = kind == DlpContentKind.BODY_HTML ? Jsoup.parse(text).text() : text;
                parts.add(part(partId, kind, fileName, contentType, extracted, extracted.length() > MAX_PART_CHARS, List.of()));
                return;
            }

            if (fileName == null && mailPart.getDisposition() == null) {
                return;
            }

            byte[] bytes = readPartBytes(mailPart, warnings, partId);
            String lowerName = fileName != null ? fileName.toLowerCase(Locale.ROOT) : "";
            if (lowerType.contains("pdf") || lowerName.endsWith(".pdf")) {
                parts.add(pdfPart(partId, fileName, contentType, bytes));
            } else if (lowerType.contains("zip") || lowerName.endsWith(".zip")) {
                extractZip(partId, fileName, contentType, bytes, parts, warnings);
            } else if (isTextAttachment(lowerName, lowerType)) {
                String text = decodeText(bytes, rawContentType);
                parts.add(part(partId, DlpContentKind.ATTACHMENT_TEXT, fileName, contentType, text, text.length() > MAX_PART_CHARS, List.of()));
            } else if (fileName != null) {
                parts.add(part(partId, DlpContentKind.ATTACHMENT_METADATA, fileName, contentType, fileName, false,
                        List.of("Unsupported attachment type")));
            }
        } catch (Exception e) {
            warnings.add("Failed to extract MIME part " + partId + ": " + e.getMessage());
        }
    }

    private DlpContentPart pdfPart(String partId, String fileName, String contentType, byte[] bytes) {
        List<String> warnings = new ArrayList<>();
        String text = "";
        try (var document = Loader.loadPDF(bytes)) {
            text = new PDFTextStripper().getText(document);
        } catch (Exception e) {
            warnings.add("PDF extraction failed: " + e.getMessage());
        }
        return part(partId, DlpContentKind.ATTACHMENT_PDF, fileName, contentType, text, text.length() > MAX_PART_CHARS, warnings);
    }

    private void extractZip(String partId,
                            String fileName,
                            String contentType,
                            byte[] bytes,
                            List<DlpContentPart> parts,
                            List<String> warnings) {
        int entries = 0;
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                entries++;
                if (entries > MAX_ZIP_ENTRIES) {
                    warnings.add("ZIP entry limit exceeded for " + nullToDash(fileName));
                    break;
                }
                String entryName = entry.getName();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    total += read;
                    if (total > MAX_ZIP_TOTAL_BYTES) {
                        warnings.add("ZIP expanded size limit exceeded for " + nullToDash(fileName));
                        break;
                    }
                    out.write(buffer, 0, read);
                    if (out.size() > MAX_ATTACHMENT_BYTES) {
                        warnings.add("ZIP entry truncated: " + entryName);
                        break;
                    }
                }
                if (isTextAttachment(entryName.toLowerCase(Locale.ROOT), "text/plain")) {
                    String text = out.toString(StandardCharsets.UTF_8);
                    parts.add(part(partId + ":" + entries,
                            DlpContentKind.ATTACHMENT_ZIP_ENTRY,
                            nullToDash(fileName) + "!" + entryName,
                            contentType,
                            text,
                            text.length() > MAX_PART_CHARS || out.size() > MAX_ATTACHMENT_BYTES,
                            List.of()));
                }
                if (total > MAX_ZIP_TOTAL_BYTES) {
                    break;
                }
            }
        } catch (Exception e) {
            warnings.add("ZIP extraction failed for " + nullToDash(fileName) + ": " + e.getMessage());
        }
    }

    private byte[] readPartBytes(Part part, List<String> warnings, String partId) throws Exception {
        try (InputStream input = part.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) > 0) {
                if (out.size() + read > MAX_ATTACHMENT_BYTES) {
                    out.write(buffer, 0, Math.max(0, MAX_ATTACHMENT_BYTES - out.size()));
                    warnings.add("Attachment truncated at " + partId);
                    break;
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private DlpContentPart part(String partId,
                                DlpContentKind kind,
                                String fileName,
                                String contentType,
                                String text,
                                boolean truncated,
                                List<String> warnings) {
        String value = text != null ? text : "";
        boolean actuallyTruncated = truncated || value.length() > MAX_PART_CHARS;
        String stored = value.length() > MAX_PART_CHARS ? value.substring(0, MAX_PART_CHARS) : value;
        List<String> partWarnings = warnings == null ? List.of() : warnings;
        if (actuallyTruncated && partWarnings.stream().noneMatch(warning -> warning.contains("truncated"))) {
            partWarnings = new ArrayList<>(partWarnings);
            partWarnings.add("Content part truncated: " + partId);
        }
        return new DlpContentPart(
                partId,
                kind,
                fileName,
                safeContentType(contentType),
                value.length(),
                stored,
                actuallyTruncated,
                partWarnings);
    }

    private String collectHeaders(MimeMessage msg) {
        StringBuilder sb = new StringBuilder();
        try {
            var headers = msg.getAllHeaderLines();
            while (headers.hasMoreElements()) {
                sb.append(headers.nextElement()).append('\n');
            }
        } catch (Exception e) {
            log.debug("Failed to collect headers: {}", e.getMessage());
        }
        return sb.toString();
    }

    private boolean isTextAttachment(String lowerName, String lowerType) {
        return lowerType.startsWith("text/")
                || lowerType.contains("json")
                || lowerType.contains("xml")
                || lowerName.endsWith(".txt")
                || lowerName.endsWith(".csv")
                || lowerName.endsWith(".json")
                || lowerName.endsWith(".xml")
                || lowerName.endsWith(".log")
                || lowerName.endsWith(".md")
                || lowerName.endsWith(".yaml")
                || lowerName.endsWith(".yml");
    }

    private boolean isInlineTextPart(String lowerType) {
        return lowerType.startsWith("text/");
    }

    private String decodeText(byte[] bytes, String rawContentType) {
        Charset charset = StandardCharsets.UTF_8;
        try {
            if (rawContentType != null && !rawContentType.isBlank()) {
                String charsetName = new ContentType(rawContentType).getParameter("charset");
                if (charsetName != null && !charsetName.isBlank()) {
                    charset = Charset.forName(charsetName.trim());
                }
            }
        } catch (Exception e) {
            log.debug("Failed to resolve MIME charset from '{}': {}", rawContentType, e.getMessage());
        }
        return new String(bytes != null ? bytes : new byte[0], charset);
    }

    private String safeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        int semicolon = contentType.indexOf(';');
        return (semicolon >= 0 ? contentType.substring(0, semicolon) : contentType).trim();
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private ExtractedContent fallbackExtract(byte[] rawContent) {
        if (rawContent == null || rawContent.length == 0) {
            return new ExtractedContent("", "");
        }
        String raw = new String(rawContent, StandardCharsets.UTF_8);
        String subject = "";
        StringBuilder body = new StringBuilder();
        boolean inBody = false;
        for (String line : raw.split("\\R")) {
            if (!inBody && line.isBlank()) {
                inBody = true;
                continue;
            }
            if (!inBody && line.regionMatches(true, 0, "Subject:", 0, "Subject:".length())) {
                subject = line.substring("Subject:".length()).trim();
                continue;
            }
            if (inBody) {
                body.append(line).append('\n');
            }
        }
        return new ExtractedContent(subject, body.toString());
    }

    public record ExtractedContent(String subject, String body) {
    }
}
