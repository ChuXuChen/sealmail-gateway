package com.sealmail.infra.mail.relay;

import com.sealmail.domain.mail.spi.SmtpRelayProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Minimal SMTP/SMTPS relay client that avoids Jakarta Mail runtime dependencies.
 */
@Component
public class SmtpRelayClient implements SmtpRelayProbe {

    private static final Logger log = LoggerFactory.getLogger(SmtpRelayClient.class);

    public void send(SmtpRelayRequest request) throws SmtpRelayException {
        try (SmtpSession session = openSession(request.connection())) {
            initializeSession(session, request.connection());

            ensureExpected(session.command("MAIL FROM:<" + request.envelopeFrom() + ">"), "MAIL FROM", 250);
            for (String recipient : request.recipients()) {
                ensureExpected(session.command("RCPT TO:<" + recipient + ">"), "RCPT TO", 250, 251);
            }
            ensureExpected(session.command("DATA"), "DATA", 354);
            session.writeData(request.messageData());
            ensureExpected(session.readResponse(), "message submission", 250);
            session.quit();
        } catch (IOException e) {
            throw new SmtpRelayException("SMTP relay I/O failed: " + e.getMessage(), e);
        }
    }

    public SmtpRelayProbeResult probe(SmtpRelayConnectionSettings connection) throws SmtpRelayException {
        try (SmtpSession session = openSession(connection)) {
            SessionState state = initializeSession(session, connection);
            session.quit();
            return new SmtpRelayProbeResult(
                    connection.host(),
                    connection.port(),
                    connection.useImplicitTls(),
                    connection.useStartTls(),
                    state.authenticated(),
                    state.capabilities()
            );
        } catch (IOException e) {
            throw new SmtpRelayException("SMTP probe I/O failed: " + e.getMessage(), e);
        }
    }

    @Override
    public SmtpProbeResult probe(SmtpConnectionSettings settings) {
        SmtpRelayProbeResult result;
        try {
            result = probe(new SmtpRelayConnectionSettings(
                    settings.host(),
                    settings.port(),
                    settings.transportSecurity(),
                    settings.username(),
                    settings.password(),
                    settings.timeoutMillis()
            ));
        } catch (SmtpRelayException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        return new SmtpProbeResult(
                result.host(),
                result.port(),
                result.implicitTls(),
                result.startTls(),
                result.authenticated(),
                result.capabilities()
        );
    }

    private SessionState initializeSession(SmtpSession session,
                                           SmtpRelayConnectionSettings connection)
            throws IOException, SmtpRelayException {
        ensureExpected(session.readResponse(), "server greeting", 220);

        SmtpResponse hello = sendHello(session);
        List<String> capabilities = hello.code() == 250 ? hello.lines() : List.of();

        if (connection.useStartTls()) {
            if (!supportsCapability(capabilities, "STARTTLS")) {
                throw new SmtpRelayException("SMTP server does not advertise STARTTLS");
            }
            ensureExpected(session.command("STARTTLS"), "STARTTLS", 220);
            session.upgradeToTls(connection.host(), connection.port(), connection.timeoutMillis());
            hello = sendHello(session);
            capabilities = hello.code() == 250 ? hello.lines() : List.of();
        }

        boolean authenticated = false;
        if (connection.hasAuthentication()) {
            authenticate(session, connection, capabilities);
            authenticated = true;
        }

        return new SessionState(List.copyOf(capabilities), authenticated);
    }

    private SmtpResponse sendHello(SmtpSession session) throws IOException, SmtpRelayException {
        String clientName = resolveClientName();
        SmtpResponse ehlo = session.command("EHLO " + clientName);
        if (ehlo.code() == 250) {
            return ehlo;
        }

        log.debug("EHLO rejected by relay, falling back to HELO: {}", ehlo.singleLine());
        SmtpResponse helo = session.command("HELO " + clientName);
        ensureExpected(helo, "HELO", 250);
        return helo;
    }

    private void authenticate(SmtpSession session,
                              SmtpRelayConnectionSettings connection,
                              List<String> capabilities)
            throws IOException, SmtpRelayException {
        Set<String> authMechanisms = advertisedAuthMechanisms(capabilities);
        SmtpRelayException loginFailure = null;

        if (authMechanisms.isEmpty() || authMechanisms.contains("LOGIN")) {
            try {
                authenticateLogin(session, connection);
                return;
            } catch (SmtpRelayException e) {
                loginFailure = e;
            }
        }

        if (authMechanisms.isEmpty() || authMechanisms.contains("PLAIN")) {
            try {
                authenticatePlain(session, connection);
                return;
            } catch (SmtpRelayException e) {
                if (loginFailure == null) {
                    loginFailure = e;
                }
            }
        }

        if (!authMechanisms.isEmpty()) {
            throw new SmtpRelayException("SMTP server does not support usable AUTH mechanisms: " + authMechanisms);
        }
        throw loginFailure != null ? loginFailure : new SmtpRelayException("SMTP authentication failed");
    }

    private void authenticateLogin(SmtpSession session, SmtpRelayConnectionSettings connection)
            throws IOException, SmtpRelayException {
        SmtpResponse start = session.command("AUTH LOGIN");
        if (start.code() == 503) {
            return;
        }
        ensureExpected(start, "AUTH LOGIN", 334);
        ensureExpected(session.command(base64(connection.username())), "AUTH LOGIN username", 334);
        ensureExpected(session.command(base64(connection.password() == null ? "" : connection.password())),
                "AUTH LOGIN password", 235);
    }

    private void authenticatePlain(SmtpSession session, SmtpRelayConnectionSettings connection)
            throws IOException, SmtpRelayException {
        String payload = "\0" + connection.username() + "\0" + (connection.password() == null ? "" : connection.password());
        SmtpResponse response = session.command("AUTH PLAIN " + base64(payload));
        if (response.code() == 503) {
            return;
        }
        ensureExpected(response, "AUTH PLAIN", 235);
    }

    private SmtpSession openSession(SmtpRelayConnectionSettings connection) throws IOException {
        SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        Socket socket = connection.useImplicitTls()
                ? sslSocketFactory.createSocket()
                : new Socket();
        socket.connect(new InetSocketAddress(connection.host(), connection.port()), connection.timeoutMillis());
        socket.setSoTimeout(connection.timeoutMillis());

        if (socket instanceof SSLSocket sslSocket) {
            sslSocket.setUseClientMode(true);
            sslSocket.startHandshake();
        }

        return new SmtpSession(socket);
    }

    private static void ensureExpected(SmtpResponse response, String operation, int... expectedCodes)
            throws SmtpRelayException {
        for (int expectedCode : expectedCodes) {
            if (response.code() == expectedCode) {
                return;
            }
        }
        throw new SmtpRelayException(operation + " failed: " + response.singleLine());
    }

    private static boolean supportsCapability(List<String> capabilities, String capability) {
        String target = capability.toUpperCase(Locale.ROOT);
        return capabilities.stream()
                .map(line -> line.toUpperCase(Locale.ROOT))
                .anyMatch(line -> line.equals(target) || line.startsWith(target + " "));
    }

    private static Set<String> advertisedAuthMechanisms(List<String> capabilities) {
        Set<String> mechanisms = new LinkedHashSet<>();
        for (String capability : capabilities) {
            String upper = capability.toUpperCase(Locale.ROOT);
            if (!upper.startsWith("AUTH")) {
                continue;
            }
            String[] parts = upper.split("\\s+");
            for (int i = 1; i < parts.length; i++) {
                mechanisms.add(parts[i]);
            }
        }
        return mechanisms;
    }

    private static String resolveClientName() {
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            if (hostName != null && !hostName.isBlank()) {
                return hostName.replaceAll("[^A-Za-z0-9.-]", "-");
            }
        } catch (Exception ignored) {
            // Fall back to a stable EHLO name.
        }
        return "sealmail.local";
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private record SessionState(List<String> capabilities, boolean authenticated) {
    }

    private record SmtpResponse(int code, List<String> lines) {

        String singleLine() {
            return code + " " + String.join(" | ", lines);
        }
    }

    private static final class SmtpSession implements AutoCloseable {

        private Socket socket;
        private InputStream input;
        private OutputStream output;

        private SmtpSession(Socket socket) throws IOException {
            this.socket = socket;
            this.input = new BufferedInputStream(socket.getInputStream());
            this.output = new BufferedOutputStream(socket.getOutputStream());
        }

        private SmtpResponse command(String command) throws IOException, SmtpRelayException {
            output.write(command.getBytes(StandardCharsets.US_ASCII));
            output.write('\r');
            output.write('\n');
            output.flush();
            return readResponse();
        }

        private SmtpResponse readResponse() throws IOException, SmtpRelayException {
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
                    String nextLine = readLine();
                    if (nextLine.length() < 4 || Integer.parseInt(nextLine.substring(0, 3)) != code) {
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

        private void writeData(byte[] data) throws IOException {
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

        private void upgradeToTls(String host, int port, int timeoutMillis) throws IOException {
            SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(socket, host, port, true);
            sslSocket.setUseClientMode(true);
            sslSocket.setSoTimeout(timeoutMillis);
            sslSocket.startHandshake();
            socket = sslSocket;
            input = new BufferedInputStream(socket.getInputStream());
            output = new BufferedOutputStream(socket.getOutputStream());
        }

        private void quit() {
            try {
                command("QUIT");
            } catch (Exception e) {
                log.debug("Failed to send QUIT to downstream relay: {}", e.getMessage());
            }
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
                    buffer.write(next);
                }
            }
            return buffer.toString(StandardCharsets.US_ASCII);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
