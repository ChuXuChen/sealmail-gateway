package com.sealmail.edge.smtp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.FutureTask;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SmtpConnectionTest {
    @Test
    void readsRawLineWithoutCrlf() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            FutureTask<byte[]> task = new FutureTask<>(() -> {
                try (Socket accepted = server.accept();
                     SmtpConnection connection = new SmtpConnection(accepted, 128)) {
                    return connection.readRawLine();
                }
            });
            Thread thread = new Thread(task);
            thread.start();

            try (Socket client = new Socket("127.0.0.1", server.getLocalPort())) {
                client.getOutputStream().write("hello\r\n".getBytes(StandardCharsets.US_ASCII));
                client.getOutputStream().flush();
            }

            assertArrayEquals("hello".getBytes(StandardCharsets.US_ASCII), task.get());
        }
    }

    @Test
    void parsesMultilineReply() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            FutureTask<SmtpReply> task = new FutureTask<>(() -> {
                try (Socket accepted = server.accept();
                     SmtpConnection connection = new SmtpConnection(accepted, 128)) {
                    connection.writeLine("250-edge");
                    connection.writeLine("250 STARTTLS");
                    return null;
                }
            });
            Thread thread = new Thread(task);
            thread.start();

            try (Socket client = new Socket("127.0.0.1", server.getLocalPort());
                 SmtpConnection connection = new SmtpConnection(client, 128)) {
                SmtpReply reply = connection.readReply();
                assertEquals(250, reply.code());
                assertEquals(2, reply.lines().size());
            } finally {
                try {
                    task.get();
                } catch (Exception e) {
                    if (e.getCause() instanceof IOException) {
                        throw e;
                    }
                }
            }
        }
    }
}
