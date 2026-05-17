package com.sealmail.infra.mailauth;

import com.sealmail.domain.mailauth.AuthenticationResultsHeader;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class AuthenticationResultsHeaderWriter {

    public byte[] prepend(byte[] content, AuthenticationResultsHeader header) {
        if (content == null || header == null || header.value() == null || header.value().isBlank()) {
            return content;
        }
        byte[] headerBytes = ("Authentication-Results: " + header.value() + "\r\n")
                .getBytes(StandardCharsets.ISO_8859_1);
        byte[] sanitizedContent = removeSameAuthservResults(content, header.authservId());
        byte[] combined = new byte[headerBytes.length + sanitizedContent.length];
        System.arraycopy(headerBytes, 0, combined, 0, headerBytes.length);
        System.arraycopy(sanitizedContent, 0, combined, headerBytes.length, sanitizedContent.length);
        return combined;
    }

    private byte[] removeSameAuthservResults(byte[] content, String authservId) {
        String message = new String(content, StandardCharsets.ISO_8859_1);
        HeaderBoundary boundary = findHeaderBoundary(message);
        if (boundary == null) {
            return content;
        }

        String filteredHeaders = filterHeaderBlock(
                message.substring(0, boundary.headerEnd()),
                authservId,
                boundary.lineSeparator());
        String body = message.substring(boundary.bodyStart());
        return (filteredHeaders + boundary.separator() + body).getBytes(StandardCharsets.ISO_8859_1);
    }

    private HeaderBoundary findHeaderBoundary(String message) {
        int crlf = message.indexOf("\r\n\r\n");
        int lf = message.indexOf("\n\n");
        if (crlf < 0 && lf < 0) {
            return null;
        }
        if (crlf >= 0 && (lf < 0 || crlf <= lf)) {
            return new HeaderBoundary(crlf, crlf + 4, "\r\n\r\n", "\r\n");
        }
        return new HeaderBoundary(lf, lf + 2, "\n\n", "\n");
    }

    private String filterHeaderBlock(String headerBlock, String authservId, String lineSeparator) {
        List<String> headers = splitHeaders(headerBlock);
        List<String> retained = headers.stream()
                .filter(value -> !isAuthenticationResultsFor(value, authservId))
                .toList();
        if (retained.size() == headers.size()) {
            return headerBlock;
        }
        return String.join(lineSeparator, retained);
    }

    private List<String> splitHeaders(String headerBlock) {
        String normalized = headerBlock.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        List<String> headers = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }
            if ((line.startsWith(" ") || line.startsWith("\t")) && current.length() > 0) {
                current.append("\r\n").append(line);
                continue;
            }
            if (current.length() > 0) {
                headers.add(current.toString());
            }
            current = new StringBuilder(line);
        }
        if (current.length() > 0) {
            headers.add(current.toString());
        }
        return headers;
    }

    private boolean isAuthenticationResultsFor(String rawHeader, String authservId) {
        int colon = rawHeader.indexOf(':');
        if (colon < 0) {
            return false;
        }
        String name = rawHeader.substring(0, colon).trim();
        if (!"Authentication-Results".equalsIgnoreCase(name)) {
            return false;
        }
        String value = rawHeader.substring(colon + 1)
                .replace("\r\n", " ")
                .replace('\n', ' ')
                .trim();
        String candidate = value.split("[;\\s]", 2)[0].trim();
        return candidate.toLowerCase(Locale.ROOT).equals(authservId.toLowerCase(Locale.ROOT));
    }

    private record HeaderBoundary(int headerEnd, int bodyStart, String separator, String lineSeparator) {
    }
}
