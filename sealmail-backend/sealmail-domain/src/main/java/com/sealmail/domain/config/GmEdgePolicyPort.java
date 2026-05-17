package com.sealmail.domain.config;

import java.time.Instant;
import java.util.List;

public interface GmEdgePolicyPort {

    GmEdgePolicySettings getSettings();

    GmEdgePolicySettings updateSettings(GmEdgePolicySettingsUpdate update);

    record GmEdgePolicySettings(
            boolean enabled,
            InboundSettings inbound,
            OutboundSettings outbound,
            PostfixSettings postfix,
            TlsSettings tls,
            LimitsSettings limits,
            List<RouteSettings> routes,
            Instant updatedAt
    ) {
        public GmEdgePolicySettings {
            routes = routes == null ? List.of() : List.copyOf(routes);
        }
    }

    record InboundSettings(
            boolean enabled,
            String bindAddress,
            int startTlsPort,
            int implicitTlsPort,
            int backlog,
            int maxConnections
    ) {
    }

    record OutboundSettings(
            boolean enabled,
            String bindAddress,
            int smartHostPort,
            int backlog,
            int maxConnections
    ) {
    }

    record PostfixSettings(
            String host,
            int port
    ) {
    }

    record TlsSettings(
            List<String> protocols,
            List<String> cipherSuites,
            String keyStorePath,
            boolean keyStorePasswordConfigured,
            String keyStorePasswordSecretRef,
            String keyStoreType,
            String trustStorePath,
            boolean trustStorePasswordConfigured,
            String trustStorePasswordSecretRef,
            String trustStoreType,
            boolean trustAll
    ) {
        public TlsSettings {
            protocols = protocols == null ? List.of() : List.copyOf(protocols);
            cipherSuites = cipherSuites == null ? List.of() : List.copyOf(cipherSuites);
        }
    }

    record LimitsSettings(
            int connectTimeoutMs,
            int readTimeoutMs,
            int maxMessageSizeBytes,
            int maxLineLengthBytes,
            int maxRecipients
    ) {
    }

    record RouteSettings(
            String domainPattern,
            String targetHost,
            int targetPort,
            String security
    ) {
    }

    record GmEdgePolicySettingsUpdate(
            Boolean enabled,
            InboundSettingsUpdate inbound,
            OutboundSettingsUpdate outbound,
            PostfixSettingsUpdate postfix,
            TlsSettingsUpdate tls,
            LimitsSettingsUpdate limits,
            List<RouteSettings> routes
    ) {
    }

    record InboundSettingsUpdate(
            Boolean enabled,
            String bindAddress,
            Integer startTlsPort,
            Integer implicitTlsPort,
            Integer backlog,
            Integer maxConnections
    ) {
    }

    record OutboundSettingsUpdate(
            Boolean enabled,
            String bindAddress,
            Integer smartHostPort,
            Integer backlog,
            Integer maxConnections
    ) {
    }

    record PostfixSettingsUpdate(
            String host,
            Integer port
    ) {
    }

    record TlsSettingsUpdate(
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
        public TlsSettingsUpdate {
            protocols = protocols == null ? null : List.copyOf(protocols);
            cipherSuites = cipherSuites == null ? null : List.copyOf(cipherSuites);
        }
    }

    record LimitsSettingsUpdate(
            Integer connectTimeoutMs,
            Integer readTimeoutMs,
            Integer maxMessageSizeBytes,
            Integer maxLineLengthBytes,
            Integer maxRecipients
    ) {
    }
}
