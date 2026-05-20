package com.sealmail.infra.mail.relay;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmtpSessionTest {

    @Test
    void readsMultilineResponse() throws Exception {
        SmtpSession session = sessionWithInput("""
                250-example.test\r
                250-AUTH LOGIN PLAIN\r
                250 SIZE 1000\r
                """);

        SmtpResponse response = session.readResponse();

        assertEquals(250, response.code());
        assertEquals("example.test", response.lines().get(0));
        assertEquals("AUTH LOGIN PLAIN", response.lines().get(1));
        assertEquals("SIZE 1000", response.lines().get(2));
    }

    @Test
    void rejectsResponseLineLongerThanLimit() throws Exception {
        String overlong = "250 " + "A".repeat(SmtpSession.MAX_RESPONSE_LINE_LENGTH + 1) + "\r\n";
        SmtpSession session = sessionWithInput(overlong);

        IOException error = assertThrows(IOException.class, session::readResponse);

        assertTrue(error.getMessage().contains("SMTP response line exceeded"));
    }

    @Test
    void rejectsMultilineResponseWithTooManyLines() throws Exception {
        StringBuilder response = new StringBuilder();
        for (int i = 0; i < SmtpSession.MAX_RESPONSE_LINES + 1; i++) {
            response.append("250-line").append(i).append("\r\n");
        }
        response.append("250 done\r\n");
        SmtpSession session = sessionWithInput(response.toString());

        SmtpRelayException error = assertThrows(SmtpRelayException.class, session::readResponse);

        assertTrue(error.getMessage().contains("SMTP response exceeded"));
    }

    @Test
    void dotStuffsDataAndNormalizesLineEndings() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SmtpSession session = new SmtpSession(new MemorySocket("", output));

        session.writeData("Subject: Test\n\n.leading\nbody".getBytes(StandardCharsets.UTF_8));

        assertEquals("Subject: Test\r\n\r\n..leading\r\nbody\r\n.\r\n", output.toString(StandardCharsets.US_ASCII));
    }

    private static SmtpSession sessionWithInput(String input) throws IOException {
        return new SmtpSession(new MemorySocket(input, new ByteArrayOutputStream()));
    }

    private static final class MemorySocket extends Socket {
        private final ByteArrayInputStream input;
        private final ByteArrayOutputStream output;

        private MemorySocket(String input, ByteArrayOutputStream output) {
            this.input = new ByteArrayInputStream(input.getBytes(StandardCharsets.US_ASCII));
            this.output = output;
        }

        @Override
        public ByteArrayInputStream getInputStream() {
            return input;
        }

        @Override
        public ByteArrayOutputStream getOutputStream() {
            return output;
        }
    }
}
