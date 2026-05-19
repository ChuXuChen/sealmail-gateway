package com.sealmail.infra.crypto;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetHeaders;
import jakarta.mail.internet.MimeBodyPart;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class SmimeMimeEntityCodec {

    MimeBodyPart parseMimeBodyPart(byte[] data) throws MessagingException, IOException {
        try (InputStream is = new ByteArrayInputStream(data)) {
            return new MimeBodyPart(is);
        } catch (Exception e) {
            InternetHeaders headers = new InternetHeaders();
            headers.addHeader("Content-Type", "application/octet-stream");
            return new MimeBodyPart(headers, data);
        }
    }

    MimeBodyPart extractMimeEntity(byte[] message) throws Exception {
        RawMimeSections sections = splitMessage(message);
        if (sections.contentHeaders().length == 0) {
            InternetHeaders headers = new InternetHeaders();
            headers.addHeader("Content-Type", "text/plain; charset=us-ascii");
            return new MimeBodyPart(headers, sections.body());
        }

        ByteArrayOutputStream entity = new ByteArrayOutputStream();
        entity.write(sections.contentHeaders());
        entity.write("\r\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        entity.write(sections.body());
        return parseMimeBodyPart(entity.toByteArray());
    }

    byte[] rebuildMessagePreservingOuterHeaders(byte[] outerHeaders, MimeBodyPart contentPart) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(outerHeaders);
        out.write(serializeMimePart(contentPart));
        return out.toByteArray();
    }

    RawMimeSections splitMessage(byte[] rawMessage) {
        int bodyStart = findBodyStart(rawMessage);
        int headerEnd = Math.max(0, bodyStart - (bodyStart >= 4
                && rawMessage[bodyStart - 4] == '\r'
                && rawMessage[bodyStart - 3] == '\n'
                && rawMessage[bodyStart - 2] == '\r'
                && rawMessage[bodyStart - 1] == '\n' ? 4 : 2));

        byte[] headerBytes = Arrays.copyOfRange(rawMessage, 0, Math.max(0, headerEnd));
        byte[] bodyBytes = Arrays.copyOfRange(rawMessage, Math.min(bodyStart, rawMessage.length), rawMessage.length);

        List<String> unfoldedHeaders = unfoldHeaders(new String(headerBytes, java.nio.charset.StandardCharsets.ISO_8859_1));
        ByteArrayOutputStream outer = new ByteArrayOutputStream();
        ByteArrayOutputStream content = new ByteArrayOutputStream();

        for (String headerLine : unfoldedHeaders) {
            int colon = headerLine.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = headerLine.substring(0, colon).trim();
            String value = headerLine.substring(colon + 1).trim();
            if (isContentHeader(name)) {
                writeHeaderLine(content, name, value);
            } else if (shouldPreserveOuterHeader(name)) {
                writeHeaderLine(outer, name, value);
            }
        }

        return new RawMimeSections(outer.toByteArray(), content.toByteArray(), bodyBytes);
    }

    private int findBodyStart(byte[] rawMessage) {
        for (int i = 0; i < rawMessage.length - 3; i++) {
            if (rawMessage[i] == '\r' && rawMessage[i + 1] == '\n'
                    && rawMessage[i + 2] == '\r' && rawMessage[i + 3] == '\n') {
                return i + 4;
            }
        }
        for (int i = 0; i < rawMessage.length - 1; i++) {
            if (rawMessage[i] == '\n' && rawMessage[i + 1] == '\n') {
                return i + 2;
            }
        }
        return rawMessage.length;
    }

    private List<String> unfoldHeaders(String rawHeaders) {
        String[] physicalLines = rawHeaders.split("\\r?\\n");
        List<String> logicalLines = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : physicalLines) {
            if (line.isEmpty()) {
                continue;
            }
            if ((line.startsWith(" ") || line.startsWith("\t")) && current.length() > 0) {
                current.append(' ').append(line.trim());
                continue;
            }
            if (current.length() > 0) {
                logicalLines.add(current.toString());
            }
            current.setLength(0);
            current.append(line);
        }

        if (current.length() > 0) {
            logicalLines.add(current.toString());
        }

        return logicalLines;
    }

    private void writeHeaderLine(OutputStream out, String name, String value) {
        try {
            out.write((name + ": " + value + "\r\n").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            throw new BcSMIMEOperations.CryptoException("写入MIME头失败", e);
        }
    }

    private boolean shouldPreserveOuterHeader(String name) {
        return !isContentHeader(name) && !isHiddenRecipientHeader(name);
    }

    private boolean isContentHeader(String name) {
        return name != null && (
                name.regionMatches(true, 0, "Content-", 0, "Content-".length())
                        || "MIME-Version".equalsIgnoreCase(name)
        );
    }

    private boolean isHiddenRecipientHeader(String name) {
        return "Bcc".equalsIgnoreCase(name) || "Resent-Bcc".equalsIgnoreCase(name);
    }

    private byte[] serializeMimePart(MimeBodyPart part) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        part.writeTo(out);
        byte[] raw = out.toByteArray();
        int start = 0;
        while (start < raw.length && (raw[start] == '\r' || raw[start] == '\n')) {
            start++;
        }
        return start == 0 ? raw : Arrays.copyOfRange(raw, start, raw.length);
    }

    record RawMimeSections(byte[] outerHeaders, byte[] contentHeaders, byte[] body) {
    }
}
