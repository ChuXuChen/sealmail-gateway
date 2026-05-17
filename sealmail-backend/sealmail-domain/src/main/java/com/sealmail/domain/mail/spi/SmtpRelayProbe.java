package com.sealmail.domain.mail.spi;

import com.sealmail.domain.mailsecurity.SmtpTransportSecurity;

import java.util.List;

public interface SmtpRelayProbe {

    SmtpProbeResult probe(SmtpConnectionSettings settings);

    record SmtpConnectionSettings(
            String host,
            int port,
            SmtpTransportSecurity transportSecurity,
            String username,
            String password,
            int timeoutMillis
    ) {
        public SmtpConnectionSettings(String host,
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
            transportSecurity = transportSecurity == null ? SmtpTransportSecurity.NONE : transportSecurity;
        }

        public boolean useTls() {
            return transportSecurity.usesTls();
        }
    }

    record SmtpProbeResult(
            String host,
            int port,
            boolean implicitTls,
            boolean startTls,
            boolean authenticated,
            List<String> capabilities
    ) {
        public SmtpProbeResult {
            capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        }

        public String summary() {
            String mode = implicitTls ? "SMTPS" : (startTls ? "STARTTLS" : "PLAIN");
            String capabilitySummary = capabilities.isEmpty() ? "none" : String.join(", ", capabilities);
            return "SUCCESS: " + host + ":" + port
                    + " mode=" + mode
                    + " auth=" + (authenticated ? "ok" : "skipped")
                    + " capabilities=" + capabilitySummary;
        }
    }
}
