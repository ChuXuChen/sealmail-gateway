package com.sealmail.infra.mail.relay;

import com.sealmail.domain.mail.spi.SmtpRelayProbe;
import com.sealmail.infra.config.properties.StandardTlsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

import static com.sealmail.infra.mail.relay.SmtpProtocolSupport.ensureExpected;
import static com.sealmail.infra.mail.relay.SmtpProtocolSupport.resolveClientName;
import static com.sealmail.infra.mail.relay.SmtpProtocolSupport.supportsCapability;

/**
 * Minimal SMTP relay client that avoids Jakarta Mail runtime dependencies.
 */
@Component
public class SmtpRelayClient implements SmtpRelayProbe {

    private static final Logger log = LoggerFactory.getLogger(SmtpRelayClient.class);

    private final SmtpTlsSupport tlsSupport;
    private final SmtpAuthHandler authHandler;

    public SmtpRelayClient(StandardTlsProperties standardTlsProperties) {
        this.tlsSupport = new SmtpTlsSupport(standardTlsProperties);
        this.authHandler = new SmtpAuthHandler();
    }

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
            authHandler.authenticate(session, connection, capabilities);
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
        SmtpTlsInfo tlsInfo = session.upgradeToTls(connection.host(), connection.port(), tlsSupport);
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
            SmtpTlsInfo tlsInfo = session.upgradeToTls(connection.host(), connection.port(), tlsSupport);
            markTls(probe, tlsInfo);
        }
        return session;
    }

    private static void stage(ProbeState probe, String stage) {
        if (probe != null) {
            probe.currentStage = stage;
        }
    }

    private static void markTls(ProbeState probe, SmtpTlsInfo tlsInfo) {
        if (probe == null || tlsInfo == null) {
            return;
        }
        probe.tlsHandshakeSucceeded = true;
        probe.protocol = tlsInfo.protocol();
        probe.cipher = tlsInfo.cipher();
        probe.peerCertificateFingerprint = tlsInfo.peerCertificateFingerprint();
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

}
