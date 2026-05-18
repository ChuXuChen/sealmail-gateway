package com.sealmail.domain.mail.spi;

import com.sealmail.domain.policy.DeliveryTransportProfile;

import java.util.List;

public interface SmtpRelayProbe {

    SmtpProbeResult probe(SmtpConnectionSettings settings);

    record SmtpConnectionSettings(
            String host,
            int port,
            String username,
            String password,
            int timeoutMillis,
            DeliveryTransportProfile transportProfile
    ) {
        public SmtpConnectionSettings {
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

        public SmtpConnectionSettings(String host,
                                      int port,
                                      String username,
                                      String password,
                                      int timeoutMillis) {
            this(host, port, username, password, timeoutMillis, DeliveryTransportProfile.SMTP_CLEAR);
        }
    }

    record SmtpProbeResult(
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
        public SmtpProbeResult {
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
}
