package com.sealmail.edge.smtp;

import java.util.Locale;

public final class SmtpAddressParser {
    private SmtpAddressParser() {
    }

    public static String parseMailboxArgument(String command, String line) {
        String argument = line.substring(command.length()).trim();
        int colon = argument.indexOf(':');
        if (colon >= 0) {
            argument = argument.substring(colon + 1).trim();
        }
        if (argument.isEmpty()) {
            throw new IllegalArgumentException("Missing mailbox");
        }
        if (argument.startsWith("<")) {
            int end = argument.indexOf('>');
            if (end < 0) {
                throw new IllegalArgumentException("Unclosed mailbox");
            }
            return argument.substring(0, end + 1);
        }
        int space = argument.indexOf(' ');
        String mailbox = space >= 0 ? argument.substring(0, space) : argument;
        return "<" + mailbox + ">";
    }

    public static String commandName(String line) {
        int space = line.indexOf(' ');
        return (space < 0 ? line : line.substring(0, space)).toUpperCase(Locale.ROOT);
    }
}
