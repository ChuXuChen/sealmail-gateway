package com.sealmail.infra.mail.inspection;

import com.sealmail.domain.dlp.DlpContentBundle;
import com.sealmail.domain.dlp.DlpContentKind;
import com.sealmail.domain.dlp.DlpContentPart;
import com.sealmail.domain.mailsecurity.AttachmentDescriptor;
import com.sealmail.domain.mailsecurity.MailInspectionBundle;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.infra.config.properties.AttachmentSecurityProperties;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Component
public class MailInspectionService {

    private static final int MAX_PART_CHARS = 512_000;

    private final Session mailSession;
    private final AttachmentSecurityProperties properties;

    public MailInspectionService(AttachmentSecurityProperties properties) {
        this.mailSession = Session.getInstance(new Properties());
        this.properties = properties != null ? properties : new AttachmentSecurityProperties();
    }

    public MailInspectionBundle inspect(byte[] rawMail, MailProcessingContext context) {
        byte[] rawContent = rawMail != null ? rawMail : new byte[0];
        List<DlpContentPart> parts = new ArrayList<>();
        List<AttachmentDescriptor> attachments = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try (ByteArrayInputStream is = new ByteArrayInputStream(rawContent)) {
            MimeMessage msg = new MimeMessage(mailSession, is);
            String subject = msg.getSubject();
            if (subject == null || subject.isBlank()) {
                subject = context != null ? context.subject() : "";
            }
            parts.add(part("subject", DlpContentKind.SUBJECT, null, "text/plain", subject, false, List.of()));
            parts.add(part("headers", DlpContentKind.HEADERS, null, "message/rfc822-headers",
                    collectHeaders(msg), false, List.of()));
            inspectPart(msg, "mime", parts, attachments, warnings, 0, List.of());
        } catch (Exception e) {
            log.warn("Failed to extract MIME part bundle: {}", e.getMessage());
            warnings.add("MIME extraction failed: " + e.getMessage());
            ExtractedContent fallback = fallbackExtract(rawContent);
            parts.add(part("subject", DlpContentKind.SUBJECT, null, "text/plain", fallback.subject(), false, List.of()));
            parts.add(part("body", DlpContentKind.BODY_TEXT, null, "text/plain", fallback.body(),
                    fallback.body().length() > MAX_PART_CHARS, warnings));
        }
        DlpContentBundle content = new DlpContentBundle(parts, warnings);
        return new MailInspectionBundle(content, attachments, warnings);
    }

    public ExtractedContent extractContent(byte[] rawContent) {
        MailInspectionBundle bundle = inspect(rawContent, null);
        String subject = bundle.dlpContent().parts().stream()
                .filter(part -> part.kind() == DlpContentKind.SUBJECT)
                .map(DlpContentPart::text)
                .findFirst()
                .orElse("");
        String body = bundle.dlpContent().parts().stream()
                .filter(part -> part.kind() == DlpContentKind.BODY_TEXT || part.kind() == DlpContentKind.BODY_HTML)
                .map(DlpContentPart::text)
                .findFirst()
                .orElse("");
        return new ExtractedContent(subject, body);
    }

    private void inspectPart(Part mailPart,
                             String partId,
                             List<DlpContentPart> parts,
                             List<AttachmentDescriptor> attachments,
                             List<String> warnings,
                             int depth,
                             List<String> nestedPath) {
        if (depth > properties.getMaxAttachmentDepth()) {
            warnings.add("MIME nesting depth exceeded at " + partId);
            return;
        }
        try {
            String rawContentType = mailPart.getContentType();
            String contentType = safeContentType(rawContentType);
            String lowerType = contentType.toLowerCase(Locale.ROOT);
            String fileName = mailPart.getFileName();
            String disposition = mailPart.getDisposition();
            boolean attachmentLike = fileName != null
                    || (disposition != null && !Part.INLINE.equalsIgnoreCase(disposition));

            if (mailPart.isMimeType("multipart/*")) {
                Object content = mailPart.getContent();
                if (!(content instanceof Multipart multipart)) {
                    warnings.add("MIME multipart content was not readable at " + partId);
                    return;
                }
                int count = multipart.getCount();
                for (int i = 0; i < count; i++) {
                    inspectPart(multipart.getBodyPart(i), partId + "." + i, parts, attachments, warnings,
                            depth + 1, nestedPath);
                }
                return;
            }

            if (isInlineTextPart(lowerType) && !attachmentLike) {
                byte[] bytes = readPartBytes(mailPart, warnings, partId).bytes();
                DlpContentKind kind = lowerType.contains("html")
                        ? DlpContentKind.BODY_HTML
                        : DlpContentKind.BODY_TEXT;
                String text = decodeText(bytes, rawContentType);
                String extracted = kind == DlpContentKind.BODY_HTML ? Jsoup.parse(text).text() : text;
                parts.add(part(partId, kind, fileName, contentType, extracted,
                        extracted.length() > MAX_PART_CHARS, List.of()));
                return;
            }

            if (fileName == null && mailPart.getDisposition() == null) {
                return;
            }

            AttachmentBytes attachmentBytes = readPartBytes(mailPart, warnings, partId);
            byte[] bytes = attachmentBytes.bytes();
            String lowerName = fileName != null ? fileName.toLowerCase(Locale.ROOT) : "";
            String extension = extension(fileName);
            String detectedMime = detectMime(bytes, fileName, contentType);
            List<String> path = nestedPath(fileName, nestedPath);
            String sha256 = sha256(bytes);

            if (isArchive(fileName, contentType, detectedMime, extension)) {
                ArchiveMetadata archive = inspectArchive(
                        partId,
                        fileName,
                        contentType,
                        detectedMime,
                        extension,
                        bytes,
                        parts,
                        attachments,
                        warnings,
                        depth + 1,
                        path);
                attachments.add(new AttachmentDescriptor(
                        partId,
                        fileName,
                        contentType,
                        detectedMime,
                        extension,
                        bytes.length,
                        sha256,
                        true,
                        archive.encrypted(),
                        attachmentBytes.truncated() || archive.truncated(),
                        archive.entryCount(),
                        archive.expandedBytes(),
                        path,
                        mergeWarnings(attachmentBytes.warnings(), archive.warnings())));
                return;
            }

            if (lowerType.contains("pdf") || "pdf".equals(extension)) {
                List<String> pdfWarnings = new ArrayList<>();
                String text = "";
                try (var document = Loader.loadPDF(bytes)) {
                    text = new PDFTextStripper().getText(document);
                } catch (Exception e) {
                    pdfWarnings.add("PDF extraction failed: " + e.getMessage());
                }
                parts.add(part(partId, DlpContentKind.ATTACHMENT_PDF, fileName, contentType, text,
                        text.length() > MAX_PART_CHARS, pdfWarnings));
                attachments.add(new AttachmentDescriptor(
                        partId,
                        fileName,
                        contentType,
                        detectedMime,
                        extension,
                        bytes.length,
                        sha256,
                        false,
                        false,
                        attachmentBytes.truncated(),
                        null,
                        null,
                        path,
                        mergeWarnings(attachmentBytes.warnings(), pdfWarnings)));
                return;
            }

            if (isTextAttachment(lowerName, lowerType, extension, detectedMime)) {
                String text = decodeText(bytes, rawContentType);
                parts.add(part(partId, DlpContentKind.ATTACHMENT_TEXT, fileName, contentType, text,
                        text.length() > MAX_PART_CHARS, List.of()));
                attachments.add(new AttachmentDescriptor(
                        partId,
                        fileName,
                        contentType,
                        detectedMime,
                        extension,
                        bytes.length,
                        sha256,
                        false,
                        false,
                        attachmentBytes.truncated(),
                        null,
                        null,
                        path,
                        attachmentBytes.warnings()));
                return;
            }

            if (fileName != null) {
                parts.add(part(partId, DlpContentKind.ATTACHMENT_METADATA, fileName, contentType, fileName,
                        false, List.of("Unsupported attachment type")));
                attachments.add(new AttachmentDescriptor(
                        partId,
                        fileName,
                        contentType,
                        detectedMime,
                        extension,
                        bytes.length,
                        sha256,
                        false,
                        false,
                        attachmentBytes.truncated(),
                        null,
                        null,
                        path,
                        mergeWarnings(attachmentBytes.warnings(), List.of("Unsupported attachment type"))));
            }
        } catch (Exception e) {
            warnings.add("Failed to extract MIME part " + partId + ": " + e.getMessage());
        }
    }

    private ArchiveMetadata inspectArchive(String partId,
                                           String fileName,
                                           String declaredMime,
                                           String detectedMime,
                                           String extension,
                                           byte[] bytes,
                                           List<DlpContentPart> parts,
                                           List<AttachmentDescriptor> attachments,
                                           List<String> warnings,
                                           int depth,
                                           List<String> nestedPath) {
        List<String> archiveWarnings = new ArrayList<>();
        if (isZipEncrypted(bytes)) {
            archiveWarnings.add("Encrypted archive detected for " + nullToDash(fileName));
            warnings.addAll(archiveWarnings);
            return new ArchiveMetadata(0, 0, true, false, archiveWarnings);
        }

        long entryCount = 0;
        long expandedBytes = 0;
        boolean truncated = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                entryCount++;
                if (entryCount > properties.getMaxZipEntries()) {
                    archiveWarnings.add("ZIP entry limit exceeded for " + nullToDash(fileName));
                    truncated = true;
                    break;
                }

                String entryName = entry.getName();
                AttachmentBytes entryBytes = readStreamBytes(zip, properties.getMaxAttachmentBytes(), archiveWarnings,
                        nullToDash(fileName) + "!" + entryName);
                byte[] entryPayload = entryBytes.bytes();
                expandedBytes += entryPayload.length;
                if (expandedBytes > properties.getMaxZipExpandedBytes()) {
                    archiveWarnings.add("ZIP expanded size limit exceeded for " + nullToDash(fileName));
                    truncated = true;
                    break;
                }

                List<String> entryPath = appendPath(nestedPath, entryName);
                String entryExtension = extension(entryName);
                String entryDeclaredMime = "application/octet-stream";
                String entryDetectedMime = detectMime(entryPayload, entryName, entryDeclaredMime);
                String entrySha256 = sha256(entryPayload);

                if (isArchive(entryName, entryDeclaredMime, entryDetectedMime, entryExtension)
                        && depth <= properties.getMaxAttachmentDepth()) {
                    ArchiveMetadata nestedArchive = inspectArchive(
                            partId + ":" + entryCount,
                            entryName,
                            entryDeclaredMime,
                            entryDetectedMime,
                            entryExtension,
                            entryPayload,
                            parts,
                            attachments,
                            warnings,
                            depth + 1,
                            entryPath);
                    attachments.add(new AttachmentDescriptor(
                            partId + ":" + entryCount,
                            entryName,
                            entryDeclaredMime,
                            entryDetectedMime,
                            entryExtension,
                            entryPayload.length,
                            entrySha256,
                            true,
                            nestedArchive.encrypted(),
                            entryBytes.truncated() || nestedArchive.truncated(),
                            nestedArchive.entryCount(),
                            nestedArchive.expandedBytes(),
                            entryPath,
                            mergeWarnings(entryBytes.warnings(), nestedArchive.warnings())));
                    continue;
                }

                if (isTextAttachment(
                        entryName.toLowerCase(Locale.ROOT),
                        entryDeclaredMime.toLowerCase(Locale.ROOT),
                        entryExtension,
                        entryDetectedMime)) {
                    String text = decodeText(entryPayload, entryDeclaredMime);
                    parts.add(part(partId + ":" + entryCount,
                            DlpContentKind.ATTACHMENT_ZIP_ENTRY,
                            nullToDash(fileName) + "!" + entryName,
                            declaredMime,
                            text,
                            text.length() > MAX_PART_CHARS || entryBytes.truncated(),
                            List.of()));
                }

                attachments.add(new AttachmentDescriptor(
                        partId + ":" + entryCount,
                        entryName,
                        entryDeclaredMime,
                        entryDetectedMime,
                        entryExtension,
                        entryPayload.length,
                        entrySha256,
                        false,
                        false,
                        entryBytes.truncated(),
                        null,
                        null,
                        entryPath,
                        entryBytes.warnings()));
            }
        } catch (Exception e) {
            archiveWarnings.add("ZIP extraction failed for " + nullToDash(fileName) + ": " + e.getMessage());
        }
        warnings.addAll(archiveWarnings);
        return new ArchiveMetadata(entryCount, expandedBytes, false, truncated, archiveWarnings);
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

    private boolean isTextAttachment(String lowerName, String lowerType, String extension, String detectedMime) {
        return lowerType.startsWith("text/")
                || lowerType.contains("json")
                || lowerType.contains("xml")
                || "text/plain".equals(detectedMime)
                || "application/json".equals(detectedMime)
                || "application/xml".equals(detectedMime)
                || lowerName.endsWith(".txt")
                || lowerName.endsWith(".csv")
                || lowerName.endsWith(".json")
                || lowerName.endsWith(".xml")
                || lowerName.endsWith(".log")
                || lowerName.endsWith(".md")
                || lowerName.endsWith(".yaml")
                || lowerName.endsWith(".yml")
                || "txt".equals(extension)
                || "csv".equals(extension)
                || "json".equals(extension)
                || "xml".equals(extension)
                || "log".equals(extension)
                || "md".equals(extension)
                || "yaml".equals(extension)
                || "yml".equals(extension);
    }

    private boolean isInlineTextPart(String lowerType) {
        return lowerType.startsWith("text/");
    }

    private boolean isArchive(String fileName, String declaredMime, String detectedMime, String extension) {
        return "zip".equals(extension)
                || "jar".equals(extension)
                || "application/zip".equalsIgnoreCase(detectedMime)
                || "application/zip".equalsIgnoreCase(declaredMime)
                || (fileName != null && (fileName.toLowerCase(Locale.ROOT).endsWith(".zip")
                || fileName.toLowerCase(Locale.ROOT).endsWith(".jar")));
    }

    private AttachmentBytes readPartBytes(Part part, List<String> warnings, String partId) throws Exception {
        try (InputStream input = part.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            return readStreamBytes(input, properties.getMaxAttachmentBytes(), warnings, partId);
        }
    }

    private AttachmentBytes readStreamBytes(InputStream input,
                                            int limit,
                                            List<String> warnings,
                                            String label) throws Exception {
        try (InputStream closeable = input;
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            boolean truncated = false;
            List<String> localWarnings = new ArrayList<>();
            while ((read = closeable.read(buffer)) > 0) {
                if (out.size() + read > limit) {
                    int allowed = Math.max(0, limit - out.size());
                    if (allowed > 0) {
                        out.write(buffer, 0, allowed);
                    }
                    truncated = true;
                    String warning = "Attachment truncated at " + label;
                    warnings.add(warning);
                    localWarnings.add(warning);
                    break;
                }
                out.write(buffer, 0, read);
            }
            return new AttachmentBytes(out.toByteArray(), truncated, localWarnings);
        }
    }

    private AttachmentBytes readStreamBytes(ZipInputStream zip,
                                            int limit,
                                            List<String> warnings,
                                            String label) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        boolean truncated = false;
        List<String> localWarnings = new ArrayList<>();
        while ((read = zip.read(buffer)) > 0) {
            if (out.size() + read > limit) {
                int allowed = Math.max(0, limit - out.size());
                if (allowed > 0) {
                    out.write(buffer, 0, allowed);
                }
                truncated = true;
                String warning = "ZIP entry truncated: " + label;
                warnings.add(warning);
                localWarnings.add(warning);
                break;
            }
            out.write(buffer, 0, read);
        }
        return new AttachmentBytes(out.toByteArray(), truncated, localWarnings);
    }

    private List<String> mergeWarnings(List<String> first, List<String> second) {
        List<String> warnings = new ArrayList<>();
        if (first != null) {
            warnings.addAll(first);
        }
        if (second != null) {
            warnings.addAll(second);
        }
        return warnings.stream().distinct().toList();
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

    private String detectMime(byte[] bytes, String fileName, String declaredMimeType) {
        if (bytes == null || bytes.length == 0) {
            return declaredMimeType != null ? declaredMimeType : "application/octet-stream";
        }
        if (startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return "application/pdf";
        }
        if (startsWith(bytes, new byte[]{0x50, 0x4B, 0x03, 0x04})
                || startsWith(bytes, new byte[]{0x50, 0x4B, 0x05, 0x06})
                || startsWith(bytes, new byte[]{0x50, 0x4B, 0x07, 0x08})) {
            return "application/zip";
        }
        if (startsWith(bytes, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return "image/png";
        }
        if (startsWith(bytes, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return "image/jpeg";
        }
        if (startsWith(bytes, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                || startsWith(bytes, "GIF89a".getBytes(StandardCharsets.US_ASCII))) {
            return "image/gif";
        }
        if (startsWith(bytes, new byte[]{0x4D, 0x5A})) {
            return "application/x-msdownload";
        }
        if (looksTextual(bytes)) {
            String extension = extension(fileName);
            if ("json".equals(extension)) {
                return "application/json";
            }
            if ("xml".equals(extension)) {
                return "application/xml";
            }
            return "text/plain";
        }
        return declaredMimeType != null ? declaredMimeType : "application/octet-stream";
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean looksTextual(byte[] bytes) {
        int sample = Math.min(bytes.length, 512);
        int printable = 0;
        for (int i = 0; i < sample; i++) {
            byte value = bytes[i];
            if (value == 0) {
                return false;
            }
            if (value == '\n' || value == '\r' || value == '\t' || (value >= 0x20 && value < 0x7F)) {
                printable++;
            }
        }
        return sample > 0 && printable * 100 / sample >= 85;
    }

    private String extension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        int lastDot = lower.lastIndexOf('.');
        if (lastDot < 0 || lastDot == lower.length() - 1) {
            return "";
        }
        return lower.substring(lastDot + 1);
    }

    private List<String> nestedPath(String fileName, List<String> current) {
        List<String> path = new ArrayList<>(current);
        if (fileName != null && !fileName.isBlank()) {
            path.add(fileName);
        }
        return path;
    }

    private List<String> appendPath(List<String> current, String fileName) {
        List<String> path = new ArrayList<>(current);
        if (fileName != null && !fileName.isBlank()) {
            path.add(fileName);
        }
        return path;
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private boolean isZipEncrypted(byte[] bytes) {
        if (bytes == null || bytes.length < 8) {
            return false;
        }
        for (int i = 0; i < bytes.length - 4; i++) {
            if (bytes[i] == 0x50 && bytes[i + 1] == 0x4B && bytes[i + 2] == 0x03 && bytes[i + 3] == 0x04) {
                int flagsOffset = i + 6;
                if (flagsOffset + 1 < bytes.length) {
                    int flags = Byte.toUnsignedInt(bytes[flagsOffset]) | (Byte.toUnsignedInt(bytes[flagsOffset + 1]) << 8);
                    return (flags & 0x0001) != 0;
                }
                return false;
            }
        }
        return false;
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes != null ? bytes : new byte[0]));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
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

    private record AttachmentBytes(byte[] bytes, boolean truncated, List<String> warnings) {
        private AttachmentBytes {
            bytes = bytes != null ? bytes : new byte[0];
            warnings = warnings != null ? List.copyOf(warnings) : List.of();
        }
    }

    private record ArchiveMetadata(long entryCount,
                                   long expandedBytes,
                                   boolean encrypted,
                                   boolean truncated,
                                   List<String> warnings) {
        private ArchiveMetadata {
            warnings = warnings != null ? List.copyOf(warnings) : List.of();
        }
    }
}
