package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.AttachmentDescriptor;
import com.sealmail.domain.mailsecurity.AttachmentSecurityAction;
import com.sealmail.domain.mailsecurity.AttachmentSecurityFinding;
import com.sealmail.domain.mailsecurity.AttachmentSecurityResult;
import com.sealmail.domain.mailsecurity.MailInspectionBundle;
import com.sealmail.domain.mailsecurity.MailProcessingStatusSnapshot;
import com.sealmail.infra.config.properties.AttachmentSecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Component
public class AttachmentSecurityService {

    private final AttachmentSecurityProperties properties;

    public AttachmentSecurityService(AttachmentSecurityProperties properties) {
        this.properties = properties != null ? properties : new AttachmentSecurityProperties();
    }

    public AttachmentSecurityResult evaluate(MailInspectionBundle bundle) {
        if (bundle == null) {
            return new AttachmentSecurityResult(
                    MailProcessingStatusSnapshot.PENDING,
                    AttachmentSecurityAction.ALLOW,
                    0,
                    0,
                    0L,
                    List.of(),
                    List.of("No inspection bundle"));
        }

        List<AttachmentDescriptor> attachments = bundle.attachments();
        List<String> warnings = new ArrayList<>(bundle.allWarnings());
        List<AttachmentSecurityFinding> findings = new ArrayList<>();
        long totalBytes = 0L;

        for (AttachmentDescriptor attachment : attachments) {
            totalBytes += Math.max(0L, attachment.size());
            findings.addAll(descriptorFindings(attachment));
            warnings.addAll(attachment.warnings());
        }

        if (attachments.size() > properties.getMaxAttachmentCount()) {
            findings.add(finding(
                    "ATTACHMENT_COUNT_LIMIT_EXCEEDED",
                    90,
                    AttachmentSecurityAction.BLOCK,
                    "Attachment count " + attachments.size() + " exceeds limit " + properties.getMaxAttachmentCount(),
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    List.of()));
        }

        if (totalBytes > properties.getMaxTotalAttachmentBytes()) {
            findings.add(finding(
                    "TOTAL_ATTACHMENT_SIZE_LIMIT_EXCEEDED",
                    95,
                    action(properties.getTotalSizeLimitAction(), AttachmentSecurityAction.BLOCK),
                    "Attachment size " + totalBytes + " exceeds limit " + properties.getMaxTotalAttachmentBytes(),
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    List.of()));
        }

        findings = findings.stream()
                .sorted(Comparator.comparingInt(AttachmentSecurityFinding::severity).reversed()
                        .thenComparing(AttachmentSecurityFinding::code))
                .toList();

        AttachmentSecurityAction action = finalAction(findings);
        int maxSeverity = findings.stream().mapToInt(AttachmentSecurityFinding::severity).max().orElse(0);
        String status = switch (action) {
            case QUARANTINE -> MailProcessingStatusSnapshot.QUARANTINED;
            case BLOCK -> MailProcessingStatusSnapshot.EXCEPTION;
            default -> MailProcessingStatusSnapshot.PASS;
        };
        if (!findings.isEmpty() && warnings.stream().anyMatch(Objects::nonNull)) {
            warnings.add("Attachment inspection completed with " + findings.size() + " findings");
        }
        return new AttachmentSecurityResult(
                status,
                action,
                maxSeverity,
                attachments.size(),
                totalBytes,
                findings,
                warnings.stream().filter(Objects::nonNull).distinct().toList());
    }

    private List<AttachmentSecurityFinding> descriptorFindings(AttachmentDescriptor attachment) {
        List<AttachmentSecurityFinding> findings = new ArrayList<>();
        String fileName = attachment.fileName();
        String extension = safeLower(attachment.extension());
        String detectedMime = safeLower(attachment.detectedMimeType());
        String declaredMime = safeLower(attachment.declaredMimeType());
        List<String> path = attachment.nestedPath();

        if (attachment.truncated()) {
            findings.add(finding(
                    "ATTACHMENT_SIZE_LIMIT_EXCEEDED",
                    100,
                    action(properties.getSizeLimitAction(), AttachmentSecurityAction.BLOCK),
                    "Attachment " + displayName(attachment) + " was truncated at the configured size limit",
                    fileName,
                    extension,
                    attachment.declaredMimeType(),
                    attachment.detectedMimeType(),
                    attachment.archive(),
                    attachment.encrypted(),
                    path));
        }

        if (attachment.archive()) {
            if (attachment.encrypted()) {
                findings.add(finding(
                        "ENCRYPTED_ARCHIVE",
                        90,
                        action(properties.getEncryptedArchiveAction(), AttachmentSecurityAction.QUARANTINE),
                        "Encrypted archive detected for " + displayName(attachment),
                        fileName,
                        extension,
                        attachment.declaredMimeType(),
                        attachment.detectedMimeType(),
                        true,
                        true,
                        path));
            }
            if (attachment.archiveEntryCount() != null
                    && attachment.archiveEntryCount() > properties.getMaxZipEntries()) {
                findings.add(finding(
                        "ARCHIVE_ENTRY_LIMIT_EXCEEDED",
                        90,
                        action(properties.getArchiveLimitAction(), AttachmentSecurityAction.BLOCK),
                        "Archive entry count " + attachment.archiveEntryCount()
                                + " exceeds limit " + properties.getMaxZipEntries(),
                        fileName,
                        extension,
                        attachment.declaredMimeType(),
                        attachment.detectedMimeType(),
                        true,
                        attachment.encrypted(),
                        path));
            }
            if (attachment.archiveExpandedBytes() != null
                    && attachment.archiveExpandedBytes() > properties.getMaxZipExpandedBytes()) {
                findings.add(finding(
                        "ARCHIVE_EXPANDED_SIZE_LIMIT_EXCEEDED",
                        95,
                        action(properties.getArchiveLimitAction(), AttachmentSecurityAction.BLOCK),
                        "Archive expanded size " + attachment.archiveExpandedBytes()
                                + " exceeds limit " + properties.getMaxZipExpandedBytes(),
                        fileName,
                        extension,
                        attachment.declaredMimeType(),
                        attachment.detectedMimeType(),
                        true,
                        attachment.encrypted(),
                        path));
            }
        }

        if (isHighRiskExtension(extension)) {
            findings.add(finding(
                    "HIGH_RISK_EXTENSION",
                    95,
                    action(properties.getHighRiskExtensionAction(), AttachmentSecurityAction.QUARANTINE),
                    "Blocked extension detected: " + extension,
                    fileName,
                    extension,
                    attachment.declaredMimeType(),
                    attachment.detectedMimeType(),
                    attachment.archive(),
                    attachment.encrypted(),
                    path));
        }

        if (hasDoubleExtension(fileName) && isHighRiskExtension(extension)) {
            findings.add(finding(
                    "DOUBLE_EXTENSION",
                    95,
                    action(properties.getDoubleExtensionAction(), AttachmentSecurityAction.QUARANTINE),
                    "Suspicious double extension detected for " + displayName(attachment),
                    fileName,
                    extension,
                    attachment.declaredMimeType(),
                    attachment.detectedMimeType(),
                    attachment.archive(),
                    attachment.encrypted(),
                    path));
        }

        if (mimeMismatch(attachment, declaredMime, detectedMime)) {
            findings.add(finding(
                    "MIME_MISMATCH",
                    40,
                    action(properties.getMimeMismatchAction(), AttachmentSecurityAction.WARN),
                    "Declared MIME " + valueOrDash(attachment.declaredMimeType())
                            + " does not match detected MIME " + valueOrDash(attachment.detectedMimeType()),
                    fileName,
                    extension,
                    attachment.declaredMimeType(),
                    attachment.detectedMimeType(),
                    attachment.archive(),
                    attachment.encrypted(),
                    path));
        }

        return findings;
    }

    private boolean mimeMismatch(AttachmentDescriptor attachment, String declaredMime, String detectedMime) {
        if (declaredMime.isBlank() && detectedMime.isBlank()) {
            return false;
        }
        String expected = expectedMime(attachment.extension());
        if (!declaredMime.isBlank()
                && !declaredMime.equals(detectedMime)
                && !"application/octet-stream".equals(detectedMime)) {
            return true;
        }
        return !expected.isBlank()
                && !expected.equals(detectedMime)
                && !"application/octet-stream".equals(detectedMime);
    }

    private String expectedMime(String extension) {
        if (extension == null || extension.isBlank()) {
            return "";
        }
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "txt", "csv", "log", "md", "yaml", "yml" -> "text/plain";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "pdf" -> "application/pdf";
            case "zip", "jar" -> "application/zip";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "exe", "dll", "com", "scr", "bat", "cmd" -> "application/x-msdownload";
            default -> "";
        };
    }

    private boolean isHighRiskExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return false;
        }
        return properties.getHighRiskExtensions().stream()
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(extension::equals);
    }

    private boolean hasDoubleExtension(String fileName) {
        if (fileName == null) {
            return false;
        }
        String[] parts = fileName.toLowerCase(Locale.ROOT).split("\\.");
        return parts.length >= 3;
    }

    private AttachmentSecurityFinding finding(String code,
                                              int severity,
                                              AttachmentSecurityAction action,
                                              String message,
                                              String fileName,
                                              String extension,
                                              String declaredMimeType,
                                              String detectedMimeType,
                                              boolean archive,
                                              boolean encrypted,
                                              List<String> nestedPath) {
        return new AttachmentSecurityFinding(
                code,
                severity,
                action,
                message,
                fileName,
                extension,
                declaredMimeType,
                detectedMimeType,
                archive,
                encrypted,
                nestedPath);
    }

    private AttachmentSecurityAction finalAction(List<AttachmentSecurityFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return AttachmentSecurityAction.ALLOW;
        }
        if (findings.stream().anyMatch(finding -> finding.action() == AttachmentSecurityAction.BLOCK)) {
            return AttachmentSecurityAction.BLOCK;
        }
        if (findings.stream().anyMatch(finding -> finding.action() == AttachmentSecurityAction.QUARANTINE)) {
            return AttachmentSecurityAction.QUARANTINE;
        }
        if (findings.stream().anyMatch(finding -> finding.action() == AttachmentSecurityAction.WARN)) {
            return AttachmentSecurityAction.WARN;
        }
        return AttachmentSecurityAction.ALLOW;
    }

    private AttachmentSecurityAction action(String raw, AttachmentSecurityAction fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return AttachmentSecurityAction.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String displayName(AttachmentDescriptor attachment) {
        if (attachment == null) {
            return "-";
        }
        if (attachment.fileName() != null && !attachment.fileName().isBlank()) {
            return attachment.fileName();
        }
        if (attachment.nestedPath() != null && !attachment.nestedPath().isEmpty()) {
            return String.join("/", attachment.nestedPath());
        }
        return "-";
    }

    private String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
