package com.sealmail.infra.mailauth;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

final class MailAuthMimeSupport {

    private MailAuthMimeSupport() {
    }

    static MimeMessage parse(byte[] content) throws Exception {
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(content));
    }

    static String fromDomain(MimeMessage message) throws Exception {
        if (message.getFrom() == null || message.getFrom().length == 0) {
            return null;
        }
        if (message.getFrom()[0] instanceof InternetAddress address) {
            return domainOf(address.getAddress());
        }
        return domainOf(message.getFrom()[0].toString());
    }

    static String domainOf(String email) {
        if (email == null) {
            return null;
        }
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return null;
        }
        return email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
    }

    static String relaxedHeaderValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\r\\n]+[\\t ]+", " ")
                .replaceAll("[\\t ]+", " ")
                .trim();
    }

    static String relaxedHeader(MimeMessage message, String name) throws Exception {
        String[] values = message.getHeader(name);
        if (values == null || values.length == 0) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT) + ":" + relaxedHeaderValue(values[values.length - 1]) + "\r\n";
    }

    static String relaxedBody(byte[] content) {
        String text = new String(content, StandardCharsets.ISO_8859_1);
        int split = text.indexOf("\r\n\r\n");
        String body = split >= 0 ? text.substring(split + 4) : "";
        body = body.replaceAll("[\\t ]+\r\n", "\r\n");
        while (body.endsWith("\r\n\r\n")) {
            body = body.substring(0, body.length() - 2);
        }
        if (!body.endsWith("\r\n")) {
            body = body + "\r\n";
        }
        return body;
    }

    static byte[] prependHeader(byte[] content, String header) {
        byte[] headerBytes = (header + "\r\n").getBytes(StandardCharsets.ISO_8859_1);
        byte[] combined = new byte[headerBytes.length + content.length];
        System.arraycopy(headerBytes, 0, combined, 0, headerBytes.length);
        System.arraycopy(content, 0, combined, headerBytes.length, content.length);
        return combined;
    }
}
