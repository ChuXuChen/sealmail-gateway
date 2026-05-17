package com.sealmail.infra.mail.relay;

import java.util.List;

/**
 * Outcome of an SMTP connection/authentication probe.
 */
public record SmtpRelayProbeResult(
        String host,
        int port,
        boolean implicitTls,
        boolean startTls,
        boolean authenticated,
        List<String> capabilities
) {

    public SmtpRelayProbeResult {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }

    public String summary() {
        String capabilitySummary = capabilities.isEmpty() ? "none" : String.join(", ", capabilities);
        return "SUCCESS: " + host + ":" + port
                + " mode=PLAIN"
                + " auth=" + (authenticated ? "ok" : "skipped")
                + " capabilities=" + capabilitySummary;
    }
}
