package com.sealmail.infra.mail.relay;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

final class SmtpProtocolSupport {

    private SmtpProtocolSupport() {
    }

    static void ensureExpected(SmtpResponse response, String operation, int... expectedCodes)
            throws SmtpRelayException {
        for (int expectedCode : expectedCodes) {
            if (response.code() == expectedCode) {
                return;
            }
        }
        throw new SmtpRelayException(operation + " failed: " + response.singleLine());
    }

    static boolean supportsCapability(List<String> capabilities, String capability) {
        String target = capability.toUpperCase(Locale.ROOT);
        return capabilities.stream()
                .map(line -> line.toUpperCase(Locale.ROOT))
                .anyMatch(line -> line.equals(target) || line.startsWith(target + " "));
    }

    static String resolveClientName() {
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            if (hostName != null && !hostName.isBlank()) {
                return hostName.replaceAll("[^A-Za-z0-9.-]", "-");
            }
        } catch (Exception ignored) {
            // Fall back to a stable EHLO name.
        }
        return "sealmail.local";
    }

    static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
