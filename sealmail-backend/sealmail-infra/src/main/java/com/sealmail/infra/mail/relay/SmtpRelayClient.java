package com.sealmail.infra.mail.relay;

import com.sealmail.domain.mail.spi.SmtpRelayProbe;
import com.sealmail.domain.policy.DeliveryTransportProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Minimal SMTP relay client that avoids Jakarta Mail runtime dependencies.
 */
@Component
public class SmtpRelayClient implements SmtpRelayProbe {

    private static final Logger log = LoggerFactory.getLogger(SmtpRelayClient.class);

    public void send(SmtpRelayRequest request) throws SmtpRelayException {
        try (SmtpSession session = openSession(request.connection(), null)) {
            initializeSession(session, request.connection(), null);

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
        ProbeState probe = new ProbeState(connection);
        try (SmtpSession session = openSession(connection, probe)) {
            SessionState state = initializeSession(session, connection, probe);
            probe.authenticated = state.authenticated();
            probe.capabilities = state.capabilities();
            session.quit();
        } catch (IOException e) {
            probe.failIfUnset(probe.currentStage, e);
        } catch (SmtpRelayException e) {
            probe.failIfUnset(probe.currentStage, e);
        }
        return probe.result();
    }

    @Override
    public SmtpProbeResult probe(SmtpConnectionSettings settings) {
        SmtpRelayProbeResult result;
        try {
            result = probe(new SmtpRelayConnectionSettings(
                    settings.host(),
                    settings.port(),
                    settings.username(),
                    settings.password(),
                    settings.timeoutMillis(),
                    settings.transportProfile()
            ));
        } catch (SmtpRelayException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        return new SmtpProbeResult(
                result.host(),
                result.port(),
                result.transportProfile(),
                result.tcpConnected(),
                result.ehloSucceeded(),
                result.startTlsAdvertised(),
                result.tlsHandshakeSucceeded(),
                result.protocol(),
                result.cipher(),
                result.peerCertificateFingerprint(),
                result.failureStage(),
                result.authenticated(),
                result.capabilities()
        );
    }

    private SessionState initializeSession(SmtpSession session,
                                           SmtpRelayConnectionSettings connection,
                                           ProbeState probe)
            throws IOException, SmtpRelayException {
        stage(probe, "GREETING");
        ensureExpected(session.readResponse(), "server greeting", 220);

        stage(probe, "EHLO");
        SmtpResponse hello = sendHello(session);
        boolean ehloSucceeded = hello.ehloSucceeded();
        List<String> capabilities = hello.code() == 250 ? hello.lines() : List.of();
        if (probe != null) {
            probe.ehloSucceeded = ehloSucceeded;
            probe.capabilities = capabilities;
            probe.startTlsAdvertised = supportsCapability(capabilities, "STARTTLS");
        }

        if (connection.transportProfile().usesStartTls()) {
            capabilities = startTls(session, connection, capabilities, probe);
        }

        boolean authenticated = false;
        if (connection.hasAuthentication()) {
            if (!session.tlsEstablished()) {
                throw new SmtpRelayException("SMTP AUTH requires an established TLS/TLCP channel");
            }
            stage(probe, "AUTH");
            authenticate(session, connection, capabilities);
            authenticated = true;
        }
        if (probe != null) {
            probe.authenticated = authenticated;
            probe.capabilities = capabilities;
        }

        return new SessionState(List.copyOf(capabilities), authenticated);
    }

    private SmtpResponse sendHello(SmtpSession session) throws IOException, SmtpRelayException {
        String clientName = resolveClientName();
        SmtpResponse ehlo = session.command("EHLO " + clientName);
        if (ehlo.code() == 250) {
            return ehlo.withEhloSucceeded(true);
        }

        log.debug("EHLO rejected by relay, falling back to HELO: {}", ehlo.singleLine());
        SmtpResponse helo = session.command("HELO " + clientName);
        ensureExpected(helo, "HELO", 250);
        return helo.withEhloSucceeded(false);
    }

    private List<String> startTls(SmtpSession session,
                                  SmtpRelayConnectionSettings connection,
                                  List<String> capabilities,
                                  ProbeState probe)
            throws IOException, SmtpRelayException {
        stage(probe, "STARTTLS");
        if (!supportsCapability(capabilities, "STARTTLS")) {
            throw new SmtpRelayException("STARTTLS not advertised by remote server");
        }
        ensureExpected(session.command("STARTTLS"), "STARTTLS", 220);

        stage(probe, "TLS_HANDSHAKE");
        if (connection.transportProfile().usesGmTls()) {
            throw new SmtpRelayException("GM STARTTLS/TLCP probing requires SealMail Edge/Kona");
        }
        TlsInfo tlsInfo = session.upgradeToTls(connection.host(), connection.port());
        markTls(probe, tlsInfo);

        stage(probe, "EHLO_AFTER_TLS");
        SmtpResponse tlsHello = sendHello(session);
        List<String> tlsCapabilities = tlsHello.code() == 250 ? tlsHello.lines() : List.of();
        if (probe != null) {
            probe.ehloSucceeded = probe.ehloSucceeded || tlsHello.ehloSucceeded();
            probe.capabilities = tlsCapabilities;
        }
        return tlsCapabilities;
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

    private SmtpSession openSession(SmtpRelayConnectionSettings connection, ProbeState probe)
            throws IOException, SmtpRelayException {
        stage(probe, "TCP_CONNECT");
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(connection.host(), connection.port()), connection.timeoutMillis());
        socket.setSoTimeout(connection.timeoutMillis());
        if (probe != null) {
            probe.tcpConnected = true;
        }

        SmtpSession session = new SmtpSession(socket);
        if (connection.transportProfile().usesImplicitTls()) {
            stage(probe, "TLS_HANDSHAKE");
            if (connection.transportProfile().usesGmTls()) {
                throw new SmtpRelayException("GM implicit TLS/TLCP probing requires SealMail Edge/Kona");
            }
            TlsInfo tlsInfo = session.upgradeToTls(connection.host(), connection.port());
            markTls(probe, tlsInfo);
        }
        return session;
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

    private static void stage(ProbeState probe, String stage) {
        if (probe != null) {
            probe.currentStage = stage;
        }
    }

    private static void markTls(ProbeState probe, TlsInfo tlsInfo) {
        if (probe == null || tlsInfo == null) {
            return;
        }
        probe.tlsHandshakeSucceeded = true;
        probe.protocol = tlsInfo.protocol();
        probe.cipher = tlsInfo.cipher();
        probe.peerCertificateFingerprint = tlsInfo.peerCertificateFingerprint();
    }

    private static TlsInfo tlsInfo(SSLSession session) {
        return new TlsInfo(
                session.getProtocol(),
                session.getCipherSuite(),
                peerFingerprint(session)
        );
    }

    private static String peerFingerprint(SSLSession session) {
        try {
            java.security.cert.Certificate[] certificates = session.getPeerCertificates();
            if (certificates.length == 0 || !(certificates[0] instanceof X509Certificate certificate)) {
                return null;
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            return hex(digest);
        } catch (SSLPeerUnverifiedException | CertificateEncodingException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02X", b));
        }
        return builder.toString();
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

    private record TlsInfo(String protocol, String cipher, String peerCertificateFingerprint) {
    }

    private record SessionState(List<String> capabilities, boolean authenticated) {
    }

    private static final class ProbeState {
        private final SmtpRelayConnectionSettings connection;
        private boolean tcpConnected;
        private boolean ehloSucceeded;
        private boolean startTlsAdvertised;
        private boolean tlsHandshakeSucceeded;
        private String protocol;
        private String cipher;
        private String peerCertificateFingerprint;
        private String failureStage;
        private boolean authenticated;
        private List<String> capabilities = List.of();
        private String currentStage = "TCP_CONNECT";

        private ProbeState(SmtpRelayConnectionSettings connection) {
            this.connection = connection;
        }

        private void failIfUnset(String stage, Exception exception) {
            if (failureStage != null && !failureStage.isBlank()) {
                return;
            }
            String message = exception.getMessage();
            failureStage = (stage == null || stage.isBlank() ? "UNKNOWN" : stage)
                    + (message == null || message.isBlank() ? "" : ": " + message);
        }

        private SmtpRelayProbeResult result() {
            return new SmtpRelayProbeResult(
                    connection.host(),
                    connection.port(),
                    connection.transportProfile(),
                    tcpConnected,
                    ehloSucceeded,
                    startTlsAdvertised,
                    tlsHandshakeSucceeded,
                    protocol,
                    cipher,
                    peerCertificateFingerprint,
                    failureStage,
                    authenticated,
                    capabilities
            );
        }
    }

    private record SmtpResponse(int code, List<String> lines, boolean ehloSucceeded) {

        private SmtpResponse(int code, List<String> lines) {
            this(code, lines, false);
        }

        private SmtpResponse withEhloSucceeded(boolean value) {
            return new SmtpResponse(code, lines, value);
        }

        String singleLine() {
            return code + " " + String.join(" | ", lines);
        }
    }

    private final class SmtpSession implements AutoCloseable {

        private Socket socket;
        private InputStream input;
        private OutputStream output;

        private SmtpSession(Socket socket) throws IOException {
            this.socket = socket;
            this.input = new BufferedInputStream(socket.getInputStream());
            this.output = new BufferedOutputStream(socket.getOutputStream());
        }

        private TlsInfo upgradeToTls(String host, int port) throws IOException {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) factory
                    .createSocket(socket, host, port, true);
            sslSocket.setUseClientMode(true);
            sslSocket.setSoTimeout(socket.getSoTimeout());
            sslSocket.startHandshake();
            replaceSocket(sslSocket);
            return tlsInfo(sslSocket.getSession());
        }

        private boolean tlsEstablished() {
            return socket instanceof SSLSocket;
        }

        private void replaceSocket(Socket upgradedSocket) throws IOException {
            this.socket = upgradedSocket;
            this.input = new BufferedInputStream(upgradedSocket.getInputStream());
            this.output = new BufferedOutputStream(upgradedSocket.getOutputStream());
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
