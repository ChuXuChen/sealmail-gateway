package com.sealmail.app.dto.response;

import java.time.Instant;
import java.util.List;

public record GmEdgePolicyResponse(
        boolean enabled,
        InboundResponse inbound,
        OutboundResponse outbound,
        PostfixResponse postfix,
        TlsResponse tls,
        LimitsResponse limits,
        List<RouteResponse> routes,
        Instant updatedAt
) {
    public GmEdgePolicyResponse {
        routes = routes == null ? List.of() : List.copyOf(routes);
    }

    public record InboundResponse(
            boolean enabled,
            String bindAddress,
            int startTlsPort,
            int implicitTlsPort,
            int backlog,
            int maxConnections
    ) {
    }

    public record OutboundResponse(
            boolean enabled,
            String bindAddress,
            int smartHostPort,
            int backlog,
            int maxConnections
    ) {
    }

    public record PostfixResponse(
            String host,
            int port
    ) {
    }

    public record TlsResponse(
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
        public TlsResponse {
            protocols = protocols == null ? List.of() : List.copyOf(protocols);
            cipherSuites = cipherSuites == null ? List.of() : List.copyOf(cipherSuites);
        }
    }

    public record LimitsResponse(
            int connectTimeoutMs,
            int readTimeoutMs,
            int maxMessageSizeBytes,
            int maxLineLengthBytes,
            int maxRecipients
    ) {
    }

    public record RouteResponse(
            String domainPattern,
            String targetHost,
            int targetPort,
            String security
    ) {
    }
}
