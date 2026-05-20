package com.sealmail.infra.mail.relay;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class SmtpAuthHandler {

    void authenticate(SmtpSession session,
                      SmtpRelayConnectionSettings connection,
                      List<String> capabilities)
            throws IOException, SmtpRelayException {
        Set<String> authMechanisms = advertisedAuthMechanisms(capabilities);
        SmtpRelayException loginFailure = null;

        if (authMechanisms.isEmpty() || authMechanisms.contains("LOGIN")) {
            try {
                authenticateLogin(session, connection);
                return;
            } catch (SmtpRelayException e) {
                loginFailure = e;
            }
        }

        if (authMechanisms.isEmpty() || authMechanisms.contains("PLAIN")) {
            try {
                authenticatePlain(session, connection);
                return;
            } catch (SmtpRelayException e) {
                if (loginFailure == null) {
                    loginFailure = e;
                }
            }
        }

        if (!authMechanisms.isEmpty()) {
            throw new SmtpRelayException("SMTP server does not support usable AUTH mechanisms: " + authMechanisms);
        }
        throw loginFailure != null ? loginFailure : new SmtpRelayException("SMTP authentication failed");
    }

    private void authenticateLogin(SmtpSession session, SmtpRelayConnectionSettings connection)
            throws IOException, SmtpRelayException {
        SmtpResponse start = session.command("AUTH LOGIN");
        if (start.code() == 503) {
            return;
        }
        SmtpProtocolSupport.ensureExpected(start, "AUTH LOGIN", 334);
        SmtpProtocolSupport.ensureExpected(session.command(SmtpProtocolSupport.base64(connection.username())),
                "AUTH LOGIN username", 334);
        SmtpProtocolSupport.ensureExpected(session.command(SmtpProtocolSupport.base64(nullablePassword(connection))),
                "AUTH LOGIN password", 235);
    }

    private void authenticatePlain(SmtpSession session, SmtpRelayConnectionSettings connection)
            throws IOException, SmtpRelayException {
        String payload = "\0" + connection.username() + "\0" + nullablePassword(connection);
        SmtpResponse response = session.command("AUTH PLAIN " + SmtpProtocolSupport.base64(payload));
        if (response.code() == 503) {
            return;
        }
        SmtpProtocolSupport.ensureExpected(response, "AUTH PLAIN", 235);
    }

    private static Set<String> advertisedAuthMechanisms(List<String> capabilities) {
        Set<String> mechanisms = new LinkedHashSet<>();
        for (String capability : capabilities) {
            String upper = capability.toUpperCase(Locale.ROOT);
            if (!upper.startsWith("AUTH")) {
                continue;
            }
            String[] parts = upper.split("\\s+");
            for (int i = 1; i < parts.length; i++) {
                mechanisms.add(parts[i]);
            }
        }
        return mechanisms;
    }

    private static String nullablePassword(SmtpRelayConnectionSettings connection) {
        return connection.password() == null ? "" : connection.password();
    }
}
