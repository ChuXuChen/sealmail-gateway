package com.sealmail.app.dto.request;

import java.util.List;

public record GmEdgePolicyRequest(
        Boolean enabled,
        InboundRequest inbound,
        OutboundRequest outbound,
        PostfixRequest postfix,
        TlsRequest tls,
        LimitsRequest limits,
        List<RouteRequest> routes
) {
    public record InboundRequest(
            Boolean enabled,
            String bindAddress,
            Integer startTlsPort,
            Integer implicitTlsPort,
            Integer backlog,
            Integer maxConnections
    ) {
    }

    public record OutboundRequest(
            Boolean enabled,
            String bindAddress,
            Integer smartHostPort,
            Integer backlog,
            Integer maxConnections
    ) {
    }

    public record PostfixRequest(
            String host,
            Integer port
    ) {
    }

    public record TlsRequest(
            List<String> protocols,
            List<String> cipherSuites,
            String keyStorePath,
            String keyStorePasswordSecretRef,
            Boolean clearKeyStorePasswordSecretRef,
            String keyStoreType,
            String trustStorePath,
            String trustStorePasswordSecretRef,
            Boolean clearTrustStorePasswordSecretRef,
            String trustStoreType,
            Boolean trustAll
    ) {
    }

    public record LimitsRequest(
            Integer connectTimeoutMs,
            Integer readTimeoutMs,
            Integer maxMessageSizeBytes,
            Integer maxLineLengthBytes,
            Integer maxRecipients
    ) {
    }

    public record RouteRequest(
            String domainPattern,
            String targetHost,
            Integer targetPort,
            String security
    ) {
    }
}
