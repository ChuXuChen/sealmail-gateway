package com.sealmail.edge.smtp;

import com.sealmail.edge.tls.EdgeTlsContextFactory;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SmtpClient implements AutoCloseable {
    private final String host;
    private final int port;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final int maxLineLengthBytes;
    private final EdgeTlsContextFactory tlsContextFactory;
    private Socket socket;
    private SmtpConnection connection;
    private List<String> capabilities = List.of();

    public SmtpClient(String host,
                      int port,
                      Duration connectTimeout,
                      Duration readTimeout,
                      int maxLineLengthBytes,
                      EdgeTlsContextFactory tlsContextFactory) {
        this.host = host;
        this.port = port;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.maxLineLengthBytes = maxLineLengthBytes;
        this.tlsContextFactory = tlsContextFactory;
    }

    public void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), Math.toIntExact(connectTimeout.toMillis()));
        socket.setSoTimeout(Math.toIntExact(readTimeout.toMillis()));
        connection = new SmtpConnection(socket, maxLineLengthBytes);
        expect(connection.readReply(), 220, "server greeting");
    }

    public void connectImplicitTls() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), Math.toIntExact(connectTimeout.toMillis()));
        socket.setSoTimeout(Math.toIntExact(readTimeout.toMillis()));
        SSLSocket sslSocket = tlsContextFactory.wrapClient(socket, host, port);
        sslSocket.startHandshake();
        socket = sslSocket;
        connection = new SmtpConnection(socket, maxLineLengthBytes);
        expect(connection.readReply(), 220, "server greeting");
    }

    public List<String> ehlo(String name) throws IOException {
        connection.writeLine("EHLO " + name);
        SmtpReply reply = connection.readReply();
        if (reply.code() != 250) {
            connection.writeLine("HELO " + name);
            expect(connection.readReply(), 250, "HELO");
            return List.of();
        }
        capabilities = capabilities(reply);
        return capabilities;
    }

    public void startTls(String name) throws IOException {
        if (capabilities.stream().noneMatch(capability -> capability.equals("STARTTLS"))) {
            throw new IOException("STARTTLS not advertised by remote server");
        }
        connection.writeLine("STARTTLS");
        expect(connection.readReply(), 220, "STARTTLS");
        SSLSocket sslSocket = tlsContextFactory.wrapClient(socket, host, port);
        sslSocket.startHandshake();
        socket = sslSocket;
        connection.replaceSocket(sslSocket);
        ehlo(name);
    }

    public SmtpReply sendMessage(String sender, List<String> recipients, byte[] message) throws IOException {
        connection.writeLine("MAIL FROM:" + sender);
        SmtpReply mail = connection.readReply();
        if (!mail.positiveCompletion()) {
            return mail;
        }

        List<String> acceptedRecipients = new ArrayList<>();
        SmtpReply lastReply = mail;
        for (String recipient : recipients) {
            connection.writeLine("RCPT TO:" + recipient);
            lastReply = connection.readReply();
            if (lastReply.positiveCompletion()) {
                acceptedRecipients.add(recipient);
            } else if (lastReply.permanentFailure()) {
                return lastReply;
            }
        }
        if (acceptedRecipients.isEmpty()) {
            return lastReply;
        }

        connection.writeLine("DATA");
        SmtpReply dataStart = connection.readReply();
        if (dataStart.code() != 354) {
            return dataStart;
        }
        SmtpDataBuffer.writeData(connection, message);
        return connection.readReply();
    }

    public SmtpReply beginData(String sender, List<String> recipients) throws IOException {
        connection.writeLine("MAIL FROM:" + sender);
        SmtpReply mail = connection.readReply();
        if (!mail.positiveCompletion()) {
            return mail;
        }

        SmtpReply lastReply = mail;
        boolean acceptedRecipient = false;
        for (String recipient : recipients) {
            connection.writeLine("RCPT TO:" + recipient);
            lastReply = connection.readReply();
            if (lastReply.positiveCompletion()) {
                acceptedRecipient = true;
            } else if (lastReply.permanentFailure()) {
                return lastReply;
            }
        }
        if (!acceptedRecipient) {
            return lastReply;
        }

        connection.writeLine("DATA");
        return connection.readReply();
    }

    public SmtpReply readReply() throws IOException {
        return connection.readReply();
    }

    public SmtpConnection connection() {
        return connection;
    }

    public void quitQuietly() {
        if (connection == null) {
            return;
        }
        try {
            connection.writeLine("QUIT");
            connection.readReply();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() throws IOException {
        if (connection != null) {
            connection.close();
        } else if (socket != null) {
            socket.close();
        }
    }

    private static void expect(SmtpReply reply, int expected, String operation) throws IOException {
        if (reply.code() != expected) {
            throw new IOException(operation + " failed: " + reply.singleLine());
        }
    }

    private static List<String> capabilities(SmtpReply reply) {
        return reply.lines().stream()
                .map(line -> line.length() > 4 ? line.substring(4).trim() : "")
                .filter(line -> !line.isBlank())
                .map(line -> line.toUpperCase(Locale.ROOT))
                .toList();
    }
}
