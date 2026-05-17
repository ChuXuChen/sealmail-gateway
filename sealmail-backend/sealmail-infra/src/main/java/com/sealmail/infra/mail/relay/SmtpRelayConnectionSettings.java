package com.sealmail.infra.mail.relay;

import com.sealmail.domain.mailsecurity.SmtpTransportSecurity;

/**
 * Connection parameters for a downstream SMTP relay.
 */
public record SmtpRelayConnectionSettings(
        String host,
        int port,
        SmtpTransportSecurity transportSecurity,
        String username,
        String password,
        int timeoutMillis
) {

    public SmtpRelayConnectionSettings(String host,
                                       int port,
                                       boolean useTls,
                                       String username,
                                       String password,
                                       int timeoutMillis) {
        this(host,
                port,
                SmtpTransportSecurity.fromLegacyUseTls(useTls, port),
                username,
                password,
                timeoutMillis);
    }

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
        transportSecurity = transportSecurity == null ? SmtpTransportSecurity.NONE : transportSecurity;
    }

    public boolean useImplicitTls() {
        return transportSecurity.usesImplicitTls();
    }

    public boolean useStartTls() {
        return transportSecurity.usesStartTls();
    }

    public boolean useTls() {
        return transportSecurity.usesTls();
    }

    public boolean hasAuthentication() {
        return username != null && !username.isBlank();
    }
}
