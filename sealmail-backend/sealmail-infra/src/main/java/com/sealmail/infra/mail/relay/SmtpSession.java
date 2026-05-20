package com.sealmail.infra.mail.relay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocket;
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

final class SmtpSession implements AutoCloseable {

    static final int MAX_RESPONSE_LINE_LENGTH = 8192;
    static final int MAX_RESPONSE_LINES = 100;

    private static final Logger log = LoggerFactory.getLogger(SmtpSession.class);

    private Socket socket;
    private InputStream input;
    private OutputStream output;

    SmtpSession(Socket socket) throws IOException {
        this.socket = socket;
        this.input = new BufferedInputStream(socket.getInputStream());
        this.output = new BufferedOutputStream(socket.getOutputStream());
    }

    SmtpTlsInfo upgradeToTls(String host, int port, SmtpTlsSupport tlsSupport) throws IOException {
        SmtpTlsSupport.Upgrade upgrade = tlsSupport.upgrade(socket, host, port);
        replaceSocket(upgrade.socket());
        return upgrade.tlsInfo();
    }

    boolean tlsEstablished() {
        return socket instanceof SSLSocket;
    }

    SmtpResponse command(String command) throws IOException, SmtpRelayException {
        output.write(command.getBytes(StandardCharsets.US_ASCII));
        output.write('\r');
        output.write('\n');
        output.flush();
        return readResponse();
    }

    SmtpResponse readResponse() throws IOException, SmtpRelayException {
        String firstLine = readLine();
        if (firstLine.length() < 3 || !Character.isDigit(firstLine.charAt(0))
                || !Character.isDigit(firstLine.charAt(1))
                || !Character.isDigit(firstLine.charAt(2))) {
            throw new SmtpRelayException("Invalid SMTP response: " + firstLine);
        }

        int code = Integer.parseInt(firstLine.substring(0, 3));
        List<String> lines = new ArrayList<>();
        lines.add(extractText(firstLine));

        if (firstLine.length() > 3 && firstLine.charAt(3) == '-') {
            while (true) {
                if (lines.size() >= MAX_RESPONSE_LINES) {
                    throw new SmtpRelayException("SMTP response exceeded " + MAX_RESPONSE_LINES + " lines");
                }
                String nextLine = readLine();
                if (nextLine.length() < 4 || !sameResponseCode(nextLine, code)) {
                    throw new SmtpRelayException("Invalid SMTP multiline response: " + nextLine);
                }
                lines.add(extractText(nextLine));
                if (nextLine.charAt(3) == ' ') {
                    break;
                }
            }
        }

        return new SmtpResponse(code, lines);
    }

    void writeData(byte[] data) throws IOException {
        boolean atLineStart = true;
        boolean previousWasCarriageReturn = false;

        for (byte datum : data) {
            int unsigned = datum & 0xff;
            if (atLineStart && unsigned == '.') {
                output.write('.');
            }
            if (unsigned == '\r') {
                output.write('\r');
                previousWasCarriageReturn = true;
                atLineStart = false;
                continue;
            }
            if (unsigned == '\n') {
                if (!previousWasCarriageReturn) {
                    output.write('\r');
                }
                output.write('\n');
                previousWasCarriageReturn = false;
                atLineStart = true;
                continue;
            }

            output.write(unsigned);
            previousWasCarriageReturn = false;
            atLineStart = false;
        }

        if (!atLineStart) {
            output.write('\r');
            output.write('\n');
        }
        output.write('.');
        output.write('\r');
        output.write('\n');
        output.flush();
    }

    void quit() {
        try {
            command("QUIT");
        } catch (Exception e) {
            log.debug("Failed to send QUIT to downstream relay: {}", e.getMessage());
        }
    }

    private void replaceSocket(Socket upgradedSocket) throws IOException {
        this.socket = upgradedSocket;
        this.input = new BufferedInputStream(upgradedSocket.getInputStream());
        this.output = new BufferedOutputStream(upgradedSocket.getOutputStream());
    }

    private static String extractText(String line) {
        return line.length() <= 4 ? "" : line.substring(4).trim();
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (true) {
            int next = input.read();
            if (next == -1) {
                if (buffer.size() == 0) {
                    throw new EOFException("SMTP server closed the connection");
                }
                break;
            }
            if (next == '\n') {
                break;
            }
            if (next != '\r') {
                if (buffer.size() >= MAX_RESPONSE_LINE_LENGTH) {
                    throw new IOException("SMTP response line exceeded " + MAX_RESPONSE_LINE_LENGTH + " bytes");
                }
                buffer.write(next);
            }
        }
        return buffer.toString(StandardCharsets.US_ASCII);
    }

    private static boolean sameResponseCode(String line, int code) {
        if (!Character.isDigit(line.charAt(0))
                || !Character.isDigit(line.charAt(1))
                || !Character.isDigit(line.charAt(2))) {
            return false;
        }
        return Integer.parseInt(line.substring(0, 3)) == code;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
