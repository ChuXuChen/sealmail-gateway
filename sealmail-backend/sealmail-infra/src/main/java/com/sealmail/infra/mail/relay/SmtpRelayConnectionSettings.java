package com.sealmail.infra.mail.relay;

import com.sealmail.domain.policy.DeliveryTransportProfile;

/**
 * Connection parameters for a downstream SMTP relay.
 */
public record SmtpRelayConnectionSettings(
        String host,
        int port,
        String username,
        String password,
        int timeoutMillis,
        DeliveryTransportProfile transportProfile
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
        transportProfile = transportProfile != null ? transportProfile : DeliveryTransportProfile.SMTP_CLEAR;
    }

    public SmtpRelayConnectionSettings(String host,
                                       int port,
                                       String username,
                                       String password,
                                       int timeoutMillis) {
        this(host, port, username, password, timeoutMillis, DeliveryTransportProfile.SMTP_CLEAR);
    }

    public boolean hasAuthentication() {
        return username != null && !username.isBlank();
    }
}
