package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.policy.DeliveryTransportProfile;

public record DeliveryRoute(
        String remoteDomain,
        String host,
        DeliveryTransportProfile transportProfile,
        int port
) {
    public DeliveryRoute {
        if (remoteDomain == null || remoteDomain.isBlank()) {
            throw new IllegalArgumentException("Remote domain cannot be blank");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Delivery host cannot be blank");
        }
        transportProfile = transportProfile != null ? transportProfile : DeliveryTransportProfile.SMTP_CLEAR;
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Delivery port must be between 1 and 65535");
        }
    }
}
