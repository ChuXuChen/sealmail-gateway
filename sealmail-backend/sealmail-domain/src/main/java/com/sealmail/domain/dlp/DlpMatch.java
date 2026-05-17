package com.sealmail.domain.dlp;

public record DlpMatch(
        DlpRule rule,
        DlpContentPart part,
        String matchedText,
        int startOffset,
        int endOffset
) {
    public DlpMatch {
        if (rule == null) {
            throw new IllegalArgumentException("DLP match rule cannot be null");
        }
        if (part == null) {
            throw new IllegalArgumentException("DLP match part cannot be null");
        }
        matchedText = matchedText != null ? matchedText : "";
        startOffset = Math.max(0, startOffset);
        endOffset = Math.max(startOffset, endOffset);
    }
}
