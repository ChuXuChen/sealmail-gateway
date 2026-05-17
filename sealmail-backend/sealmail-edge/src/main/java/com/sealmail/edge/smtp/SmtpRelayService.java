package com.sealmail.edge.smtp;

import com.sealmail.edge.tls.EdgeTlsContextFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

public final class SmtpRelayService {
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final int maxLineLengthBytes;
    private final EdgeTlsContextFactory tlsContextFactory;
    private final String clientName;

    public SmtpRelayService(Duration connectTimeout,
                            Duration readTimeout,
                            int maxLineLengthBytes,
                            EdgeTlsContextFactory tlsContextFactory,
                            String clientName) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.maxLineLengthBytes = maxLineLengthBytes;
        this.tlsContextFactory = tlsContextFactory;
        this.clientName = clientName;
    }

    public SmtpReply relay(RelayTarget target, String sender, List<String> recipients, byte[] message) throws IOException {
        try (SmtpClient client = new SmtpClient(
                target.host(),
                target.port(),
                connectTimeout,
                readTimeout,
                maxLineLengthBytes,
                tlsContextFactory
        )) {
            if (target.implicitTls()) {
                client.connectImplicitTls();
            } else {
                client.connect();
            }
            client.ehlo(clientName);
            if (target.startTlsRequired()) {
                client.startTls(clientName);
            }
            SmtpReply reply = client.sendMessage(sender, recipients, message);
            client.quitQuietly();
            return reply;
        }
    }

    public SmtpClient open(RelayTarget target) throws IOException {
        SmtpClient client = new SmtpClient(
                target.host(),
                target.port(),
                connectTimeout,
                readTimeout,
                maxLineLengthBytes,
                tlsContextFactory
        );
        if (target.implicitTls()) {
            client.connectImplicitTls();
        } else {
            client.connect();
        }
        client.ehlo(clientName);
        if (target.startTlsRequired()) {
            client.startTls(clientName);
        }
        return client;
    }
}
