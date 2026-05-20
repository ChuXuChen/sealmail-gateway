package com.sealmail.infra.mail.relay;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SmtpRelayRequestTest {

    private final SmtpRelayConnectionSettings connection = new SmtpRelayConnectionSettings(
            "127.0.0.1",
            25,
            "",
            "",
            5000);

    @Test
    void rejectsEnvelopeSenderCommandInjectionCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new SmtpRelayRequest(
                connection,
                "sender@example.com>\r\nRCPT TO:<attacker@example.com",
                List.of("recipient@example.com"),
                "Subject: Test\r\n\r\nbody".getBytes(StandardCharsets.UTF_8)
        ));
    }

    @Test
    void rejectsRecipientCommandInjectionCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new SmtpRelayRequest(
                connection,
                "sender@example.com",
                List.of("recipient@example.com\nDATA"),
                "Subject: Test\r\n\r\nbody".getBytes(StandardCharsets.UTF_8)
        ));
    }

    @Test
    void rejectsAngleAddressSyntaxAtEnvelopeBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new SmtpRelayRequest(
                connection,
                "<sender@example.com>",
                List.of("recipient@example.com"),
                "Subject: Test\r\n\r\nbody".getBytes(StandardCharsets.UTF_8)
        ));
    }
}
