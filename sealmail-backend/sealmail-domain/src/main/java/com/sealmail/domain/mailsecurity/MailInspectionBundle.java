package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.dlp.DlpContentBundle;

import java.util.List;
import java.util.stream.Stream;

public record MailInspectionBundle(
        DlpContentBundle dlpContent,
        List<AttachmentDescriptor> attachments,
        List<String> warnings
) {
    public MailInspectionBundle {
        dlpContent = dlpContent != null ? dlpContent : new DlpContentBundle(List.of(), List.of());
        attachments = attachments != null ? List.copyOf(attachments) : List.of();
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }

    public List<String> allWarnings() {
        return Stream.concat(
                        Stream.concat(warnings.stream(), dlpContent.allWarnings().stream()),
                        attachments.stream().flatMap(attachment -> attachment.warnings().stream()))
                .distinct()
                .toList();
    }
}
