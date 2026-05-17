package com.sealmail.edge.smtp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class SmtpDataBuffer {
    private final int maxMessageSizeBytes;

    public SmtpDataBuffer(int maxMessageSizeBytes) {
        this.maxMessageSizeBytes = maxMessageSizeBytes;
    }

    public byte[] readData(SmtpConnection client) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        while (true) {
            String line = client.readLine();
            if (".".equals(line)) {
                return data.toByteArray();
            }
            if (line.startsWith("..")) {
                line = line.substring(1);
            }
            byte[] bytes = (line + "\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            if (data.size() + bytes.length > maxMessageSizeBytes) {
                throw new MessageTooLargeException("Message exceeds " + maxMessageSizeBytes + " bytes");
            }
            data.writeBytes(bytes);
        }
    }

    public int maxMessageSizeBytes() {
        return maxMessageSizeBytes;
    }

    public int relayData(SmtpConnection client, SmtpConnection remote) throws IOException {
        int size = 0;
        while (true) {
            byte[] line = client.readRawLine();
            if (line.length == 1 && line[0] == '.') {
                remote.writeLine(".");
                return size;
            }
            byte[] payload = line;
            if (line.length > 1 && line[0] == '.' && line[1] == '.') {
                payload = java.util.Arrays.copyOfRange(line, 1, line.length);
            }
            size += payload.length + 2;
            if (size > maxMessageSizeBytes) {
                throw new MessageTooLargeException("Message exceeds " + maxMessageSizeBytes + " bytes");
            }
            remote.writeRawDataLine(payload);
        }
    }

    public static void writeData(SmtpConnection remote, byte[] data) throws IOException {
        int lineStart = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                writeDataLine(remote, data, lineStart, i + 1);
                lineStart = i + 1;
            }
        }
        if (lineStart < data.length) {
            writeDataLine(remote, data, lineStart, data.length);
            remote.writeBytes("\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }
        remote.writeLine(".");
    }

    private static void writeDataLine(SmtpConnection remote, byte[] data, int start, int end) throws IOException {
        int contentStart = start;
        if (contentStart < end && data[contentStart] == '.') {
            remote.writeBytes(new byte[]{'.'});
        }
        remote.writeBytes(java.util.Arrays.copyOfRange(data, start, end));
    }

    public static final class MessageTooLargeException extends IOException {
        public MessageTooLargeException(String message) {
            super(message);
        }
    }
}
