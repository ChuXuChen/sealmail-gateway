package com.sealmail.infra.dlp;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * MIME 内容提取器
 * 从原始邮件字节数据中提取主题和正文用于 DLP 扫描
 */
@Slf4j
@Component
public class MimeContentExtractor {

    private final Session mailSession;

    public MimeContentExtractor() {
        this.mailSession = Session.getInstance(new Properties());
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
