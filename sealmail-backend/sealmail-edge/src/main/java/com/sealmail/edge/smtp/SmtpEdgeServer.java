package com.sealmail.edge.smtp;

import com.sealmail.edge.metrics.EdgeMetrics;
import com.sealmail.edge.tls.EdgeTlsContextFactory;

import javax.net.ssl.SSLSocket;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SmtpEdgeServer implements AutoCloseable {
    private static final Logger log = Logger.getLogger(SmtpEdgeServer.class.getName());

    private final String name;
    private final String bindAddress;
    private final int port;
    private final int backlog;
    private final Mode mode;
    private final int maxConnections;
    private final int maxRecipients;
    private final Duration readTimeout;
    private final int maxLineLengthBytes;
    private final SmtpDataBuffer dataBuffer;
    private final EdgeTlsContextFactory tlsContextFactory;
    private final RelayTargetResolver targetResolver;
    private final SmtpRelayService relayService;
    private final EdgeMetrics metrics;
    private final boolean traceHeadersEnabled;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger activeConnections = new AtomicInteger();
    private final Semaphore connectionPermits;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private ServerSocket serverSocket;

    public SmtpEdgeServer(String name,
                          String bindAddress,
                          int port,
                          int backlog,
                          Mode mode,
                          int maxConnections,
                          int maxRecipients,
                          Duration readTimeout,
                          int maxLineLengthBytes,
                          SmtpDataBuffer dataBuffer,
                          EdgeTlsContextFactory tlsContextFactory,
                          RelayTargetResolver targetResolver,
                          SmtpRelayService relayService,
                          EdgeMetrics metrics,
                          boolean traceHeadersEnabled) {
        this.name = name;
        this.bindAddress = bindAddress;
        this.port = port;
        this.backlog = backlog;
        this.mode = mode;
        this.maxConnections = maxConnections;
        this.maxRecipients = maxRecipients;
        this.readTimeout = readTimeout;
        this.maxLineLengthBytes = maxLineLengthBytes;
        this.dataBuffer = dataBuffer;
        this.tlsContextFactory = tlsContextFactory;
        this.targetResolver = targetResolver;
        this.relayService = relayService;
        this.metrics = metrics;
        this.traceHeadersEnabled = traceHeadersEnabled;
        this.connectionPermits = new Semaphore(maxConnections);
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        serverSocket = new ServerSocket(port, backlog, InetAddress.getByName(bindAddress));
        executor.submit(this::acceptLoop);
        log.info(() -> name + " listening on " + bindAddress + ":" + port + " mode=" + mode);
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(Math.toIntExact(readTimeout.toMillis()));
                if (!connectionPermits.tryAcquire()) {
                    metrics.connectionRejected();
                    rejectOverloaded(socket);
                    continue;
                }
                activeConnections.incrementAndGet();
                metrics.connectionAccepted();
                executor.submit(() -> handle(socket));
            } catch (IOException e) {
                if (running.get()) {
                    log.log(Level.WARNING, name + " accept failed", e);
                }
            }
        }
    }

    private void rejectOverloaded(Socket socket) {
        executor.submit(() -> {
            try (socket; SmtpConnection connection = new SmtpConnection(socket, maxLineLengthBytes)) {
                connection.writeReply(421, "Too many connections");
            } catch (IOException e) {
                log.fine(() -> name + " overload rejection failed: " + e.getMessage());
            }
        });
    }

    private void handle(Socket socket) {
        try (socket) {
            Socket activeSocket = socket;
            if (mode == Mode.IMPLICIT_TLS) {
                SSLSocket sslSocket = tlsContextFactory.wrapServer(socket);
                sslSocket.startHandshake();
                activeSocket = sslSocket;
            }
            try (SmtpConnection client = new SmtpConnection(activeSocket, maxLineLengthBytes)) {
                runSession(client, activeSocket, socket);
            }
        } catch (EOFException e) {
            log.fine(() -> name + " peer closed connection: " + e.getMessage());
        } catch (SocketException e) {
            log.fine(() -> name + " socket closed: " + e.getMessage());
        } catch (IOException e) {
            log.log(Level.INFO, name + " session failed: " + e.getMessage(), e);
        } finally {
            activeConnections.decrementAndGet();
            connectionPermits.release();
            metrics.sessionCompleted();
        }
    }

    private void runSession(SmtpConnection client, Socket activeSocket, Socket originalSocket) throws IOException {
        SmtpEnvelope envelope = new SmtpEnvelope();
        boolean tlsActive = mode == Mode.IMPLICIT_TLS;
        boolean greeted = false;
        String remoteAddress = originalSocket.getInetAddress().getHostAddress();

        client.writeReply(220, "sealmail kona edge ready");
        while (true) {
            String line = client.readLine();
            String command = SmtpAddressParser.commandName(line);
            switch (command) {
                case "EHLO" -> {
                    greeted = true;
                    client.writeMultilineReply(250, capabilities(tlsActive));
                }
                case "HELO" -> {
                    greeted = true;
                    client.writeReply(250, "sealmail kona edge");
                }
                case "STARTTLS" -> {
                    if (mode != Mode.STARTTLS) {
                        client.writeReply(503, "STARTTLS not available");
                    } else if (tlsActive) {
                        client.writeReply(503, "TLS already active");
                    } else {
                        client.writeReply(220, "Ready to start TLCP or ShangMi TLS");
                        SSLSocket sslSocket = tlsContextFactory.wrapServer(activeSocket);
                        sslSocket.startHandshake();
                        client.replaceSocket(sslSocket);
                        activeSocket = sslSocket;
                        tlsActive = true;
                        greeted = false;
                        envelope.reset();
                    }
                }
                case "MAIL" -> {
                    if (!greeted) {
                        client.writeReply(503, "Send EHLO first");
                    } else if (mode == Mode.STARTTLS && !tlsActive) {
                        client.writeReply(530, "Must issue STARTTLS first");
                    } else {
                        try {
                            envelope.setSender(SmtpAddressParser.parseMailboxArgument("MAIL", line));
                            client.writeReply(250, "Sender OK");
                        } catch (IllegalArgumentException e) {
                            client.writeReply(501, e.getMessage());
                        }
                    }
                }
                case "RCPT" -> {
                    if (!envelope.hasSender()) {
                        client.writeReply(503, "Need MAIL before RCPT");
                    } else if (envelope.recipientCount() >= maxRecipients) {
                        client.writeReply(452, "Too many recipients");
                    } else {
                        try {
                            envelope.addRecipient(SmtpAddressParser.parseMailboxArgument("RCPT", line));
                            client.writeReply(250, "Recipient OK");
                        } catch (IllegalArgumentException e) {
                            client.writeReply(501, e.getMessage());
                        }
                    }
                }
                case "DATA" -> handleData(client, envelope, remoteAddress, edgeProtocol(activeSocket));
                case "RSET" -> {
                    envelope.reset();
                    client.writeReply(250, "Reset OK");
                }
                case "NOOP" -> client.writeReply(250, "OK");
                case "AUTH" -> client.writeReply(503, "Authentication is not available on GM Edge");
                case "VRFY", "EXPN" -> client.writeReply(252, "Cannot verify users");
                case "HELP" -> client.writeReply(214, "Supported commands: EHLO HELO STARTTLS MAIL RCPT DATA RSET NOOP QUIT");
                case "QUIT" -> {
                    client.writeReply(221, "Bye");
                    return;
                }
                default -> client.writeReply(502, "Command not implemented");
            }
        }
    }

    private void handleData(SmtpConnection client,
                            SmtpEnvelope envelope,
                            String remoteAddress,
                            String edgeProtocol) throws IOException {
        if (!envelope.hasSender() || !envelope.hasRecipients()) {
            client.writeReply(503, "Need MAIL and RCPT before DATA");
            return;
        }

        Optional<RelayTarget> target = targetResolver.resolve(envelope.recipients());
        if (target.isEmpty()) {
            client.writeReply(554, "No configured GM route for recipient domain");
            metrics.messageFailed(554);
            return;
        }

        try (SmtpClient remote = relayService.open(target.get())) {
            SmtpReply dataStart = remote.beginData(envelope.sender(), envelope.recipients());
            if (dataStart.code() != 354) {
                client.writeReply(dataStart.code(), replyText(dataStart));
                metrics.messageFailed(dataStart.code());
                envelope.reset();
                return;
            }
            client.writeReply(354, "End data with <CR><LF>.<CR><LF>");
            if (traceHeadersEnabled) {
                writeTraceHeaders(remote.connection(), remoteAddress, edgeProtocol);
            }
            int relayedBytes = dataBuffer.relayData(client, remote.connection());
            SmtpReply relayReply = remote.readReply();
            remote.quitQuietly();
            client.writeReply(relayReply.code(), replyText(relayReply));
            if (relayReply.positiveCompletion()) {
                metrics.messageRelayed(relayedBytes);
                envelope.reset();
            } else {
                metrics.messageFailed(relayReply.code());
            }
        } catch (SmtpDataBuffer.MessageTooLargeException e) {
            client.writeReply(552, e.getMessage());
            metrics.oversizedMessage();
            envelope.reset();
        } catch (IOException e) {
            client.writeReply(451, "GM relay temporary failure: " + e.getMessage());
            metrics.messageFailed(451);
            envelope.reset();
        }
    }

    private static void writeTraceHeaders(SmtpConnection remote, String remoteAddress, String edgeProtocol) throws IOException {
        remote.writeRawDataLine(("X-Original-Client-IP: " + sanitizeHeader(remoteAddress))
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        remote.writeRawDataLine(("X-SealMail-Edge-Protocol: " + sanitizeHeader(edgeProtocol))
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static String sanitizeHeader(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ');
    }

    private static String edgeProtocol(Socket socket) {
        if (socket instanceof SSLSocket sslSocket) {
            return sslSocket.getSession().getProtocol() + " " + sslSocket.getSession().getCipherSuite();
        }
        return "CLEAR_INTERNAL";
    }

    private List<String> capabilities(boolean tlsActive) {
        if (mode == Mode.STARTTLS && !tlsActive) {
            return List.of(
                    "sealmail kona edge",
                    "STARTTLS",
                    "SIZE " + dataBuffer.maxMessageSizeBytes(),
                    "8BITMIME"
            );
        }
        return List.of(
                "sealmail kona edge",
                "SIZE " + dataBuffer.maxMessageSizeBytes(),
                "8BITMIME"
        );
    }

    private static String replyText(SmtpReply reply) {
        String line = reply.singleLine();
        return line.length() > 4 ? line.substring(4).trim() : line;
    }

    @Override
    public void close() throws IOException {
        running.set(false);
        if (serverSocket != null) {
            serverSocket.close();
        }
        executor.shutdownNow();
    }

    public boolean running() {
        return running.get();
    }

    public Status status() {
        return new Status(name, bindAddress, port, mode.name(), running.get(), activeConnections.get(), maxConnections);
    }

    public record Status(
            String name,
            String bindAddress,
            int port,
            String mode,
            boolean running,
            int activeConnections,
            int maxConnections
    ) {
    }

    public enum Mode {
        STARTTLS,
        IMPLICIT_TLS,
        CLEAR_INTERNAL
    }
}
