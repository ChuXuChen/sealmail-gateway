package com.sealmail.edge.smtp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmtpAddressParserTest {
    @Test
    void parsesBracketedMailbox() {
        assertEquals("<sender@example.cn>",
                SmtpAddressParser.parseMailboxArgument("MAIL", "MAIL FROM:<sender@example.cn> SIZE=123"));
    }

    @Test
    void wrapsBareMailbox() {
        assertEquals("<rcpt@example.cn>",
                SmtpAddressParser.parseMailboxArgument("RCPT", "RCPT TO:rcpt@example.cn"));
    }

    @Test
    void extractsCommandName() {
        assertEquals("STARTTLS", SmtpAddressParser.commandName("starttls"));
    }
}
