package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.policy.DeliveryTransportProfile;

public record RelayProfile(
        String host,
        int port,
        String username,
        String password,
        int timeout,
        String envelopeFrom,
        DeliveryTransportProfile transportProfile
) {

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
        transportProfile = transportProfile != null ? transportProfile : DeliveryTransportProfile.SMTP_CLEAR;
    }

    public RelayProfile(String host,
                        int port,
                        String username,
                        String password,
                        int timeout,
                        String envelopeFrom) {
        this(host, port, username, password, timeout, envelopeFrom, DeliveryTransportProfile.SMTP_CLEAR);
    }
}
