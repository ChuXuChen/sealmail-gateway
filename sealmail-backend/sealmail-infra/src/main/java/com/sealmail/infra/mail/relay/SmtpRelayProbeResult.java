package com.sealmail.infra.mail.relay;

import com.sealmail.domain.policy.DeliveryTransportProfile;

import java.util.List;

/**
 * Outcome of an SMTP connection/authentication probe.
 */
public record SmtpRelayProbeResult(
        String host,
        int port,
        DeliveryTransportProfile transportProfile,
        boolean tcpConnected,
        boolean ehloSucceeded,
        boolean startTlsAdvertised,
        boolean tlsHandshakeSucceeded,
        String protocol,
        String cipher,
        String peerCertificateFingerprint,
        String failureStage,
        boolean authenticated,
        List<String> capabilities
) {

    public SmtpRelayProbeResult {
        transportProfile = transportProfile != null ? transportProfile : DeliveryTransportProfile.SMTP_CLEAR;
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }

    public boolean implicitTls() {
        return transportProfile.usesImplicitTls();
    }

    public boolean startTls() {
        return transportProfile.usesStartTls();
    }

    public String summary() {
        String capabilitySummary = capabilities.isEmpty() ? "none" : String.join(", ", capabilities);
        String status = failureStage == null || failureStage.isBlank() ? "SUCCESS" : "FAILED";
        return status + ": " + host + ":" + port
                + " profile=" + transportProfile
                + " tcp=" + tcpConnected
                + " ehlo=" + ehloSucceeded
                + " starttlsAdvertised=" + startTlsAdvertised
                + " tls=" + tlsHandshakeSucceeded
                + (protocol != null && !protocol.isBlank() ? " protocol=" + protocol : "")
                + (cipher != null && !cipher.isBlank() ? " cipher=" + cipher : "")
                + (peerCertificateFingerprint != null && !peerCertificateFingerprint.isBlank()
                ? " peerFingerprint=" + peerCertificateFingerprint : "")
                + (failureStage != null && !failureStage.isBlank() ? " failureStage=" + failureStage : "")
                + " auth=" + (authenticated ? "ok" : "skipped")
                + " capabilities=" + capabilitySummary;
    }
}
