package com.sealmail.domain.system;

import java.util.List;

public record GmEdgeSettings(
        boolean enabled,
        Inbound inbound,
        Outbound outbound,
        Postfix postfix,
        Tls tls,
        Limits limits,
        List<Route> routes
) {
    public GmEdgeSettings {
        routes = routes == null ? List.of() : List.copyOf(routes);
    }

    public record Inbound(
            boolean enabled,
            String bindAddress,
            int startTlsPort,
            int implicitTlsPort,
            int backlog,
            int maxConnections
    ) {
    }

    public record Outbound(
            boolean enabled,
            String bindAddress,
            int smartHostPort,
            int backlog,
            int maxConnections
    ) {
    }

    public record Postfix(
            String host,
            int port
    ) {
    }

    public record Tls(
            List<String> protocols,
            List<String> cipherSuites,
            String keyStorePath,
            boolean keyStoreConfigured,
            boolean keyStorePasswordConfigured,
            String keyStorePasswordSecretRef,
            String keyStoreType,
            String trustStorePath,
            boolean trustStoreConfigured,
            boolean trustStorePasswordConfigured,
            String trustStorePasswordSecretRef,
            String trustStoreType,
            boolean trustAll
    ) {
        public Tls {
            protocols = protocols == null ? List.of() : List.copyOf(protocols);
            cipherSuites = cipherSuites == null ? List.of() : List.copyOf(cipherSuites);
        }
    }

    public record Limits(
            int connectTimeoutMs,
            int readTimeoutMs,
            int maxMessageSizeBytes,
            int maxLineLengthBytes,
            int maxRecipients
    ) {
    }

    public record Route(
            String domainPattern,
            String targetHost,
            int targetPort,
            String security
    ) {
    }
}
