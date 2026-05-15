package com.sealmail.infra.mail.relay;

/**
 * Connection parameters for a downstream SMTP relay.
 */
public record SmtpRelayConnectionSettings(
        String host,
        int port,
        boolean useTls,
        String username,
        String password,
        int timeoutMillis
) {

    public SmtpRelayConnectionSettings {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("SMTP relay host must not be blank");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("SMTP relay port must be between 1 and 65535");
        }
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("SMTP relay timeout must be positive");
        }
    }

    public boolean useImplicitTls() {
        return port == 465;
    }

    public boolean useStartTls() {
        return !useImplicitTls() && useTls;
    }

    public boolean hasAuthentication() {
        return username != null && !username.isBlank();
    }
}
