package com.sealmail.infra.mail.relay;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmtpAuthHandlerTest {

    @Test
    void usesPlainWhenOnlyPlainIsAdvertised() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SmtpSession session = new SmtpSession(new MemorySocket("235 2.7.0 authenticated\r\n", output));

        new SmtpAuthHandler().authenticate(session, connection(), List.of("AUTH PLAIN"));

        assertEquals("AUTH PLAIN AHVzZXIAc2VjcmV0\r\n", output.toString(StandardCharsets.US_ASCII));
    }

    @Test
    void prefersLoginWhenAdvertised() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SmtpSession session = new SmtpSession(new MemorySocket("""
                334 VXNlcm5hbWU6\r
                334 UGFzc3dvcmQ6\r
                235 2.7.0 authenticated\r
                """, output));

        new SmtpAuthHandler().authenticate(session, connection(), List.of("AUTH LOGIN PLAIN"));

        assertEquals("""
                AUTH LOGIN\r
                dXNlcg==\r
                c2VjcmV0\r
                """, output.toString(StandardCharsets.US_ASCII));
    }

    private static SmtpRelayConnectionSettings connection() {
        return new SmtpRelayConnectionSettings("127.0.0.1", 25, "user", "secret", 5000);
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
