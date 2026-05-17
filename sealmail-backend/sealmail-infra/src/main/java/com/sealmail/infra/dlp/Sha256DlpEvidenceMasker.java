package com.sealmail.infra.dlp;

import com.sealmail.domain.dlp.DlpEvidence;
import com.sealmail.domain.dlp.DlpMaskingStrategy;
import com.sealmail.domain.dlp.DlpMatch;
import com.sealmail.domain.dlp.spi.DlpEvidenceMasker;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class Sha256DlpEvidenceMasker implements DlpEvidenceMasker {

    private static final int CONTEXT_CHARS = 24;

    @Override
    public DlpEvidence mask(String eventId, DlpMatch match) {
        String matched = match.matchedText();
        String snippet = snippet(match);
        return new DlpEvidence(
                UUID.randomUUID().toString(),
                eventId,
                match.rule().id(),
                match.rule().name(),
                match.rule().type(),
                match.part().partId(),
                match.part().kind(),
                match.part().fileName(),
                match.part().contentType(),
                snippet,
                sha256(matched),
                match.startOffset(),
                match.endOffset(),
                match.rule().severity(),
                match.rule().defaultAction(),
                Instant.now()
        );
    }

    private String snippet(DlpMatch match) {
        if (match.rule().maskingStrategy() == DlpMaskingStrategy.HASH_ONLY) {
            return "[hash-only]";
        }
        String text = match.part().text();
        int start = Math.max(0, match.startOffset() - CONTEXT_CHARS);
        int end = Math.min(text.length(), match.endOffset() + CONTEXT_CHARS);
        String prefix = start > 0 ? "..." : "";
        String suffix = end < text.length() ? "..." : "";
        String before = text.substring(start, match.startOffset());
        String after = text.substring(match.endOffset(), end);
        return truncate(prefix + before + mask(match.matchedText(), match.rule().maskingStrategy()) + after + suffix);
    }

    private String mask(String value, DlpMaskingStrategy strategy) {
        if (value == null || value.isBlank()) {
            return "[masked]";
        }
        return switch (strategy) {
            case FULL -> "[masked]";
            case EMAIL -> maskEmail(value);
            case SECRET -> value.length() <= 6 ? "******" : value.substring(0, 2) + "******" + value.substring(value.length() - 2);
            case PARTIAL, DEFAULT -> partial(value);
            case HASH_ONLY -> "[hash-only]";
        };
    }

    private String partial(String value) {
        if (value.length() <= 4) {
            return "*".repeat(value.length());
        }
        int keep = Math.min(3, value.length() / 4);
        return value.substring(0, keep) + "*".repeat(Math.min(12, value.length() - keep * 2)) + value.substring(value.length() - keep);
    }

    private String maskEmail(String value) {
        int at = value.indexOf('@');
        if (at <= 1) {
            return partial(value);
        }
        return value.charAt(0) + "***" + value.substring(at);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String truncate(String value) {
        return value.length() <= 1024 ? value : value.substring(0, 1024);
    }
}
