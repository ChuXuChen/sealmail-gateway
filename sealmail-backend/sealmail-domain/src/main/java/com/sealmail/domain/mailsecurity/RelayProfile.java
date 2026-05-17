package com.sealmail.domain.mailsecurity;

public record RelayProfile(
        String host,
        int port,
        SmtpTransportSecurity transportSecurity,
        String username,
        String password,
        int timeout,
        String envelopeFrom
) {

    public RelayProfile(String host,
                        int port,
                        boolean useTls,
                        String username,
                        String password,
                        int timeout,
                        String envelopeFrom) {
        this(host,
                port,
                SmtpTransportSecurity.fromLegacyUseTls(useTls, port),
                username,
                password,
                timeout,
                envelopeFrom);
    }

    public RelayProfile {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Relay host cannot be blank");
        }
        if (port <= 0) {
            throw new IllegalArgumentException("Relay port must be positive");
        }
        username = username != null ? username : "";
        password = password != null ? password : "";
        if (timeout < 0) {
            throw new IllegalArgumentException("Relay timeout cannot be negative");
        }
        transportSecurity = transportSecurity == null ? SmtpTransportSecurity.NONE : transportSecurity;
    }

    public boolean useTls() {
        return transportSecurity.usesTls();
    }
}
