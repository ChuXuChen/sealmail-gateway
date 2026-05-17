package com.sealmail.infra.mail.relay;

import com.sealmail.domain.mailsecurity.SmtpTransportSecurity;
import com.sealmail.infra.config.properties.TransportTlsProperties;
import com.sealmail.infra.tls.TransportTlsContextFactory;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmtpRelayClientTest {

    @Test
    void sendsMessageThroughPlainSmtpWithAuthLogin() throws Exception {
        try (FakeSmtpServer fakeServer = new FakeSmtpServer()) {
            fakeServer.start();

            SmtpRelayClient smtpRelayClient = new SmtpRelayClient(
                    new TransportTlsContextFactory(new TransportTlsProperties()));
            byte[] message = "Subject: Test\n\n.leading line\nsecond line".getBytes(StandardCharsets.UTF_8);

            smtpRelayClient.send(new SmtpRelayRequest(
                    new SmtpRelayConnectionSettings("127.0.0.1", fakeServer.port(), false,
                            "relay@example.com", "secret", 5000),
                    "relay@example.com",
                    List.of("alice@example.com", "bob@example.com"),
                    message
            ));

            fakeServer.awaitCompletion();

            assertTrue(fakeServer.commands().get(0).startsWith("EHLO "));
            assertTrue(fakeServer.commands().contains("AUTH LOGIN"));
            assertTrue(fakeServer.commands().contains("cmVsYXlAZXhhbXBsZS5jb20="));
            assertTrue(fakeServer.commands().contains("c2VjcmV0"));
            assertTrue(fakeServer.commands().contains("MAIL FROM:<relay@example.com>"));
            assertTrue(fakeServer.commands().contains("RCPT TO:<alice@example.com>"));
            assertTrue(fakeServer.commands().contains("RCPT TO:<bob@example.com>"));
            assertEquals("..leading line", fakeServer.dataLines().get(2));
        }
    }

    @Test
    void connectionSettingsDoNotTreatPort465AsImplicitTlsUnlessModeRequestsSmpts() {
        SmtpRelayConnectionSettings plainOn465 = new SmtpRelayConnectionSettings(
                "127.0.0.1", 465, SmtpTransportSecurity.NONE, "", "", 5000);
        SmtpRelayConnectionSettings smtps = new SmtpRelayConnectionSettings(
                "127.0.0.1", 465, SmtpTransportSecurity.SMTPS, "", "", 5000);

        assertFalse(plainOn465.useTls());
        assertFalse(plainOn465.useImplicitTls());
        assertTrue(smtps.useTls());
        assertTrue(smtps.useImplicitTls());
    }

    private static final class FakeSmtpServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final CountDownLatch completion = new CountDownLatch(1);
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final List<String> commands = new CopyOnWriteArrayList<>();
        private final List<String> dataLines = new CopyOnWriteArrayList<>();
        private Thread serverThread;

        private FakeSmtpServer() throws IOException {
            this.serverSocket = new ServerSocket(0);
        }

        private int port() {
            return serverSocket.getLocalPort();
        }

        private List<String> commands() {
            return commands;
        }

        private List<String> dataLines() {
            return dataLines;
        }

        private void start() {
            serverThread = new Thread(this::run, "fake-smtp-server");
            serverThread.start();
        }

        private void awaitCompletion() throws Exception {
            assertTrue(completion.await(5, TimeUnit.SECONDS), "SMTP session did not complete");
            if (failure.get() != null) {
                throw new AssertionError("Fake SMTP server failed", failure.get());
            }
        }

        private void run() {
            try (Socket socket = serverSocket.accept();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII))) {

                writeLine(writer, "220 fake-smtp.example ESMTP");

                boolean readingData = false;
                int authStage = 0;

                while (true) {
                    String line = reader.readLine();
                    if (line == null) {
                        break;
                    }

                    if (readingData) {
                        if (".".equals(line)) {
                            writeLine(writer, "250 2.0.0 queued");
                            readingData = false;
                        } else {
                            dataLines.add(line);
                        }
                        continue;
                    }

                    commands.add(line);

                    if (authStage == 1) {
                        writeLine(writer, "334 UGFzc3dvcmQ6");
                        authStage = 2;
                        continue;
                    }
                    if (authStage == 2) {
                        writeLine(writer, "235 2.7.0 authenticated");
                        authStage = 0;
                        continue;
                    }

                    if (line.startsWith("EHLO ")) {
                        writeLine(writer, "250-fake-smtp.example");
                        writeLine(writer, "250-AUTH LOGIN PLAIN");
                        writeLine(writer, "250 SIZE 52428800");
                    } else if ("AUTH LOGIN".equals(line)) {
                        writeLine(writer, "334 VXNlcm5hbWU6");
                        authStage = 1;
                    } else if (line.startsWith("MAIL FROM:")) {
                        writeLine(writer, "250 2.1.0 sender ok");
                    } else if (line.startsWith("RCPT TO:")) {
                        writeLine(writer, "250 2.1.5 recipient ok");
                    } else if ("DATA".equals(line)) {
                        writeLine(writer, "354 End data with <CR><LF>.<CR><LF>");
                        readingData = true;
                    } else if ("QUIT".equals(line)) {
                        writeLine(writer, "221 2.0.0 bye");
                        break;
                    } else {
                        writeLine(writer, "250 ok");
                    }
                }
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                completion.countDown();
            }
        }

        private static void writeLine(BufferedWriter writer, String line) throws IOException {
            writer.write(line);
            writer.write("\r\n");
            writer.flush();
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            if (serverThread != null) {
                serverThread.join(TimeUnit.SECONDS.toMillis(1));
            }
        }
    }
}
