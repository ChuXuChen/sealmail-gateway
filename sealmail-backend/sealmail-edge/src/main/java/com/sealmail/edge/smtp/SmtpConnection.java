package com.sealmail.edge.smtp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class SmtpConnection implements AutoCloseable {
    private final Socket socket;
    private final int maxLineLengthBytes;
    private InputStream input;
    private OutputStream output;

    public SmtpConnection(Socket socket, int maxLineLengthBytes) throws IOException {
        this.socket = socket;
        this.maxLineLengthBytes = maxLineLengthBytes;
        this.input = new BufferedInputStream(socket.getInputStream());
        this.output = new BufferedOutputStream(socket.getOutputStream());
    }

    public void replaceSocket(Socket socket) throws IOException {
        this.input = new BufferedInputStream(socket.getInputStream());
        this.output = new BufferedOutputStream(socket.getOutputStream());
    }

    public String readLine() throws IOException {
        return new String(readRawLine(), StandardCharsets.US_ASCII);
    }

    public byte[] readRawLine() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int previous = -1;
        while (true) {
            int next = input.read();
            if (next < 0) {
                if (buffer.size() == 0) {
                    throw new EOFException("SMTP peer closed the connection");
                }
                break;
            }
            buffer.write(next);
            if (buffer.size() > maxLineLengthBytes) {
                throw new IOException("SMTP line exceeds " + maxLineLengthBytes + " bytes");
            }
            if (previous == '\r' && next == '\n') {
                break;
            }
            previous = next;
        }
        byte[] line = buffer.toByteArray();
        if (line.length >= 2 && line[line.length - 2] == '\r' && line[line.length - 1] == '\n') {
            return java.util.Arrays.copyOf(line, line.length - 2);
        }
        return line;
    }

    public void writeLine(String line) throws IOException {
        output.write(line.getBytes(StandardCharsets.US_ASCII));
        output.write('\r');
        output.write('\n');
        output.flush();
    }

    public void writeReply(int code, String text) throws IOException {
        writeLine(code + " " + text);
    }

    public void writeMultilineReply(int code, List<String> lines) throws IOException {
        if (lines.isEmpty()) {
            writeReply(code, "");
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            char separator = i == lines.size() - 1 ? ' ' : '-';
            writeLine(code + String.valueOf(separator) + lines.get(i));
        }
    }

    public SmtpReply readReply() throws IOException {
        String firstLine = readLine();
        if (firstLine.length() < 3 || !firstLine.substring(0, 3).chars().allMatch(Character::isDigit)) {
            throw new IOException("Invalid SMTP reply: " + firstLine);
        }
        int code = Integer.parseInt(firstLine.substring(0, 3));
        List<String> lines = new ArrayList<>();
        lines.add(firstLine);
        if (firstLine.length() > 3 && firstLine.charAt(3) == '-') {
            while (true) {
                String nextLine = readLine();
                lines.add(nextLine);
                if (nextLine.length() < 4 || !nextLine.substring(0, 3).equals(firstLine.substring(0, 3))) {
                    throw new IOException("Invalid SMTP multiline reply: " + nextLine);
                }
                if (nextLine.charAt(3) == ' ') {
                    break;
                }
            }
        }
        return new SmtpReply(code, lines);
    }

    public void writeBytes(byte[] data) throws IOException {
        output.write(data);
        output.flush();
    }

    public void writeRawDataLine(byte[] line) throws IOException {
        if (line.length > 0 && line[0] == '.') {
            output.write('.');
        }
        output.write(line);
        output.write('\r');
        output.write('\n');
        output.flush();
    }

    public InputStream input() {
        return input;
    }

    public OutputStream output() {
        return output;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
