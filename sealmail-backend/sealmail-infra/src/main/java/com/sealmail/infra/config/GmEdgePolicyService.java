package com.sealmail.infra.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.config.GmEdgePolicyPort;
import com.sealmail.domain.policy.event.GmEdgePolicyChanged;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.GmEdgePolicyEntity;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional
public class GmEdgePolicyService implements GmEdgePolicyPort {

    private static final String DEFAULT_ID = "default";
    private static final Set<String> ALLOWED_PROTOCOLS = Set.of("TLCPV1.1", "TLCP", "TLSV1.3");
    private static final TypeReference<List<RouteRecord>> ROUTE_LIST = new TypeReference<>() {
    };

    private final EntityManager entityManager;
    private final DomainEventPublisher domainEventPublisher;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate initializationTransaction;

    public GmEdgePolicyService(EntityManager entityManager,
                               DomainEventPublisher domainEventPublisher,
                               ObjectMapper objectMapper,
                               PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.domainEventPublisher = domainEventPublisher;
        this.objectMapper = objectMapper;
        this.initializationTransaction = new TransactionTemplate(transactionManager);
        this.initializationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional(readOnly = true)
    public GmEdgePolicySettings getSettings() {
        return toSettings(entity());
    }

    @Override
    public GmEdgePolicySettings updateSettings(GmEdgePolicySettingsUpdate update) {
        GmEdgePolicyEntity entity = entity();
        applyUpdate(entity, update);
        validateEntity(entity);
        entity.setUpdatedAt(Instant.now());
        GmEdgePolicyEntity saved = entityManager.merge(entity);
        domainEventPublisher.publishEvent(new GmEdgePolicyChanged(DEFAULT_ID, changedFields(update)));
        return toSettings(saved);
    }

    private GmEdgePolicyEntity entity() {
        GmEdgePolicyEntity entity = entityManager.find(GmEdgePolicyEntity.class, DEFAULT_ID);
        if (entity != null) {
            return entity;
        }
        return initializationTransaction.execute(status -> {
            GmEdgePolicyEntity existing = entityManager.find(GmEdgePolicyEntity.class, DEFAULT_ID);
            if (existing != null) {
                return existing;
            }
            GmEdgePolicyEntity created = defaultEntity();
            entityManager.persist(created);
            entityManager.flush();
            return created;
        });
    }

    private GmEdgePolicyEntity defaultEntity() {
        Instant now = Instant.now();
        GmEdgePolicyEntity entity = new GmEdgePolicyEntity();
        entity.setId(DEFAULT_ID);
        entity.setEnabled(false);
        entity.setInboundEnabled(true);
        entity.setInboundBindAddress("0.0.0.0");
        entity.setInboundStartTlsPort(2525);
        entity.setInboundImplicitTlsPort(2465);
        entity.setInboundBacklog(128);
        entity.setInboundMaxConnections(1024);
        entity.setOutboundEnabled(true);
        entity.setOutboundBindAddress("127.0.0.1");
        entity.setOutboundSmartHostPort(2526);
        entity.setOutboundBacklog(128);
        entity.setOutboundMaxConnections(512);
        entity.setPostfixHost("127.0.0.1");
        entity.setPostfixPort(2530);
        entity.setTlsProtocols(join(List.of("TLCPv1.1", "TLCP", "TLSv1.3")));
        entity.setTlsCipherSuites(join(List.of("TLS_SM4_GCM_SM3", "TLS_SM4_CCM_SM3")));
        entity.setTlsKeyStorePath(null);
        entity.setTlsKeyStorePasswordSecretRef(null);
        entity.setTlsKeyStoreType("PKCS12");
        entity.setTlsTrustStorePath(null);
        entity.setTlsTrustStorePasswordSecretRef(null);
        entity.setTlsTrustStoreType("PKCS12");
        entity.setTlsTrustAll(false);
        entity.setConnectTimeoutMs(10000);
        entity.setReadTimeoutMs(60000);
        entity.setMaxMessageSizeBytes(52428800);
        entity.setMaxLineLengthBytes(16384);
        entity.setMaxRecipients(100);
        entity.setRoutesJson("[]");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private void applyUpdate(GmEdgePolicyEntity entity, GmEdgePolicySettingsUpdate update) {
        if (update == null) {
            return;
        }
        if (update.enabled() != null) {
            entity.setEnabled(update.enabled());
        }
        applyInboundUpdate(entity, update.inbound());
        applyOutboundUpdate(entity, update.outbound());
        applyPostfixUpdate(entity, update.postfix());
        applyTlsUpdate(entity, update.tls());
        applyLimitsUpdate(entity, update.limits());
        if (update.routes() != null) {
            entity.setRoutesJson(writeRoutes(validateRoutes(update.routes())));
        }
    }

    private void applyInboundUpdate(GmEdgePolicyEntity entity, InboundSettingsUpdate update) {
        if (update == null) {
            return;
        }
        if (update.enabled() != null) {
            entity.setInboundEnabled(update.enabled());
        }
        if (update.bindAddress() != null) {
            entity.setInboundBindAddress(requireText(update.bindAddress(), "GM Edge inbound bind address cannot be blank"));
        }
        if (update.startTlsPort() != null) {
            entity.setInboundStartTlsPort(requirePort(update.startTlsPort(), "GM Edge inbound STARTTLS port"));
        }
        if (update.implicitTlsPort() != null) {
            entity.setInboundImplicitTlsPort(requirePort(update.implicitTlsPort(), "GM Edge inbound implicit TLS port"));
        }
        if (update.backlog() != null) {
            entity.setInboundBacklog(requirePositive(update.backlog(), "GM Edge inbound backlog"));
        }
        if (update.maxConnections() != null) {
            entity.setInboundMaxConnections(requirePositive(update.maxConnections(), "GM Edge inbound max connections"));
        }
    }

    private void applyOutboundUpdate(GmEdgePolicyEntity entity, OutboundSettingsUpdate update) {
        if (update == null) {
            return;
        }
        if (update.enabled() != null) {
            entity.setOutboundEnabled(update.enabled());
        }
        if (update.bindAddress() != null) {
            entity.setOutboundBindAddress(requireText(update.bindAddress(), "GM Edge outbound bind address cannot be blank"));
        }
        if (update.smartHostPort() != null) {
            entity.setOutboundSmartHostPort(requirePort(update.smartHostPort(), "GM Edge outbound smart host port"));
        }
        if (update.backlog() != null) {
            entity.setOutboundBacklog(requirePositive(update.backlog(), "GM Edge outbound backlog"));
        }
        if (update.maxConnections() != null) {
            entity.setOutboundMaxConnections(requirePositive(update.maxConnections(), "GM Edge outbound max connections"));
        }
    }

    private void applyPostfixUpdate(GmEdgePolicyEntity entity, PostfixSettingsUpdate update) {
        if (update == null) {
            return;
        }
        if (update.host() != null) {
            entity.setPostfixHost(requireText(update.host(), "GM Edge Postfix host cannot be blank"));
        }
        if (update.port() != null) {
            entity.setPostfixPort(requirePort(update.port(), "GM Edge Postfix port"));
        }
    }

    private void applyTlsUpdate(GmEdgePolicyEntity entity, TlsSettingsUpdate update) {
        if (update == null) {
            return;
        }
        if (update.protocols() != null) {
            List<String> protocols = normalizeList(update.protocols(), "GM Edge TLS protocol");
            require(!protocols.isEmpty(), "GM Edge TLS protocols cannot be empty");
            for (String protocol : protocols) {
                require(ALLOWED_PROTOCOLS.contains(protocol.toUpperCase(Locale.ROOT)),
                        "GM Edge only supports TLCP and ShangMi TLS 1.3 protocols");
            }
            entity.setTlsProtocols(join(protocols));
        }
        if (update.cipherSuites() != null) {
            entity.setTlsCipherSuites(join(normalizeList(update.cipherSuites(), "GM Edge TLS cipher suite")));
        }
        if (update.keyStorePath() != null) {
            entity.setTlsKeyStorePath(blankToNull(update.keyStorePath()));
        }
        if (Boolean.TRUE.equals(update.clearKeyStorePasswordSecretRef())) {
            entity.setTlsKeyStorePasswordSecretRef(null);
        } else if (update.keyStorePasswordSecretRef() != null) {
            entity.setTlsKeyStorePasswordSecretRef(blankToNull(update.keyStorePasswordSecretRef()));
        }
        if (update.keyStoreType() != null) {
            entity.setTlsKeyStoreType(requireText(update.keyStoreType(), "GM Edge key store type cannot be blank"));
        }
        if (update.trustStorePath() != null) {
            entity.setTlsTrustStorePath(blankToNull(update.trustStorePath()));
        }
        if (Boolean.TRUE.equals(update.clearTrustStorePasswordSecretRef())) {
            entity.setTlsTrustStorePasswordSecretRef(null);
        } else if (update.trustStorePasswordSecretRef() != null) {
            entity.setTlsTrustStorePasswordSecretRef(blankToNull(update.trustStorePasswordSecretRef()));
        }
        if (update.trustStoreType() != null) {
            entity.setTlsTrustStoreType(requireText(update.trustStoreType(), "GM Edge trust store type cannot be blank"));
        }
        if (update.trustAll() != null) {
            entity.setTlsTrustAll(update.trustAll());
        }
    }

    private void applyLimitsUpdate(GmEdgePolicyEntity entity, LimitsSettingsUpdate update) {
        if (update == null) {
            return;
        }
        if (update.connectTimeoutMs() != null) {
            entity.setConnectTimeoutMs(requirePositive(update.connectTimeoutMs(), "GM Edge connect timeout"));
        }
        if (update.readTimeoutMs() != null) {
            entity.setReadTimeoutMs(requirePositive(update.readTimeoutMs(), "GM Edge read timeout"));
        }
        if (update.maxMessageSizeBytes() != null) {
            entity.setMaxMessageSizeBytes(requirePositive(update.maxMessageSizeBytes(), "GM Edge max message size"));
        }
        if (update.maxLineLengthBytes() != null) {
            entity.setMaxLineLengthBytes(requirePositive(update.maxLineLengthBytes(), "GM Edge max line length"));
        }
        if (update.maxRecipients() != null) {
            entity.setMaxRecipients(requirePositive(update.maxRecipients(), "GM Edge max recipients"));
        }
    }

    private void validateEntity(GmEdgePolicyEntity entity) {
        requirePort(entity.getInboundStartTlsPort(), "GM Edge inbound STARTTLS port");
        requirePort(entity.getInboundImplicitTlsPort(), "GM Edge inbound implicit TLS port");
        requirePort(entity.getOutboundSmartHostPort(), "GM Edge outbound smart host port");
        requirePort(entity.getPostfixPort(), "GM Edge Postfix port");
        requirePositive(entity.getInboundBacklog(), "GM Edge inbound backlog");
        requirePositive(entity.getInboundMaxConnections(), "GM Edge inbound max connections");
        requirePositive(entity.getOutboundBacklog(), "GM Edge outbound backlog");
        requirePositive(entity.getOutboundMaxConnections(), "GM Edge outbound max connections");
        requirePositive(entity.getConnectTimeoutMs(), "GM Edge connect timeout");
        requirePositive(entity.getReadTimeoutMs(), "GM Edge read timeout");
        requirePositive(entity.getMaxMessageSizeBytes(), "GM Edge max message size");
        requirePositive(entity.getMaxLineLengthBytes(), "GM Edge max line length");
        requirePositive(entity.getMaxRecipients(), "GM Edge max recipients");
        requireNoPortConflict(entity);
    }

    private void requireNoPortConflict(GmEdgePolicyEntity entity) {
        if (!entity.isEnabled()) {
            return;
        }
        if (entity.isInboundEnabled() && entity.getInboundStartTlsPort() == entity.getInboundImplicitTlsPort()) {
            throw new IllegalArgumentException("GM Edge inbound STARTTLS and implicit TLS ports must be different");
        }
        if (entity.isInboundEnabled() && entity.isOutboundEnabled()
                && bindAddressesOverlap(entity.getInboundBindAddress(), entity.getOutboundBindAddress())) {
            require(entity.getOutboundSmartHostPort() != entity.getInboundStartTlsPort()
                            && entity.getOutboundSmartHostPort() != entity.getInboundImplicitTlsPort(),
                    "GM Edge outbound smart host port conflicts with inbound listener ports");
        }
    }

    private boolean bindAddressesOverlap(String left, String right) {
        String a = normalizeAddress(left);
        String b = normalizeAddress(right);
        return a.equals(b) || isWildcardAddress(a) || isWildcardAddress(b);
    }

    private boolean isWildcardAddress(String address) {
        return "*".equals(address) || "0.0.0.0".equals(address) || "::".equals(address) || "[::]".equals(address);
    }

    private String normalizeAddress(String address) {
        return requireText(address, "GM Edge bind address cannot be blank").toLowerCase(Locale.ROOT);
    }

    private List<RouteSettings> validateRoutes(List<RouteSettings> input) {
        List<RouteSettings> routes = new ArrayList<>();
        LinkedHashSet<String> patterns = new LinkedHashSet<>();
        for (RouteSettings route : input) {
            if (route == null) {
                continue;
            }
            String pattern = requireText(route.domainPattern(), "GM Edge route domain pattern cannot be blank")
                    .toLowerCase(Locale.ROOT);
            require(pattern.length() <= 253, "GM Edge route domain pattern is too long");
            require(!pattern.contains(" "), "GM Edge route domain pattern cannot contain spaces");
            require(pattern.equals("*") || pattern.matches("(\\*\\.)?[a-z0-9][a-z0-9._-]*")
                            || pattern.matches("\\.[a-z0-9][a-z0-9._-]*"),
                    "GM Edge route domain pattern is invalid");
            require(patterns.add(pattern), "Duplicate GM Edge route domain pattern: " + pattern);
            String host = requireText(route.targetHost(), "GM Edge route target host cannot be blank");
            require(!host.contains("\r") && !host.contains("\n"), "GM Edge route target host is invalid");
            int port = requirePort(route.targetPort(), "GM Edge route target port");
            String security = route.security() == null ? "STARTTLS" : route.security().trim().toUpperCase(Locale.ROOT);
            require(security.equals("STARTTLS") || security.equals("IMPLICIT_TLS"),
                    "GM Edge route security must be STARTTLS or IMPLICIT_TLS");
            routes.add(new RouteSettings(pattern, host, port, security));
        }
        return routes;
    }

    private GmEdgePolicySettings toSettings(GmEdgePolicyEntity entity) {
        return new GmEdgePolicySettings(
                entity.isEnabled(),
                new InboundSettings(
                        entity.isInboundEnabled(),
                        entity.getInboundBindAddress(),
                        entity.getInboundStartTlsPort(),
                        entity.getInboundImplicitTlsPort(),
                        entity.getInboundBacklog(),
                        entity.getInboundMaxConnections()),
                new OutboundSettings(
                        entity.isOutboundEnabled(),
                        entity.getOutboundBindAddress(),
                        entity.getOutboundSmartHostPort(),
                        entity.getOutboundBacklog(),
                        entity.getOutboundMaxConnections()),
                new PostfixSettings(
                        entity.getPostfixHost(),
                        entity.getPostfixPort()),
                new TlsSettings(
                        split(entity.getTlsProtocols()),
                        split(entity.getTlsCipherSuites()),
                        entity.getTlsKeyStorePath(),
                        hasText(entity.getTlsKeyStorePasswordSecretRef()),
                        entity.getTlsKeyStorePasswordSecretRef(),
                        entity.getTlsKeyStoreType(),
                        entity.getTlsTrustStorePath(),
                        hasText(entity.getTlsTrustStorePasswordSecretRef()),
                        entity.getTlsTrustStorePasswordSecretRef(),
                        entity.getTlsTrustStoreType(),
                        entity.isTlsTrustAll()),
                new LimitsSettings(
                        entity.getConnectTimeoutMs(),
                        entity.getReadTimeoutMs(),
                        entity.getMaxMessageSizeBytes(),
                        entity.getMaxLineLengthBytes(),
                        entity.getMaxRecipients()),
                readRoutes(entity.getRoutesJson()),
                entity.getUpdatedAt());
    }

    private List<RouteSettings> readRoutes(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, ROUTE_LIST).stream()
                    .map(route -> new RouteSettings(
                            route.domainPattern(),
                            route.targetHost(),
                            route.targetPort(),
                            route.security()))
                    .toList();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to read GM Edge routes", e);
        }
    }

    private String writeRoutes(List<RouteSettings> routes) {
        try {
            return objectMapper.writeValueAsString(routes.stream()
                    .map(route -> new RouteRecord(
                            route.domainPattern(),
                            route.targetHost(),
                            route.targetPort(),
                            route.security()))
                    .toList());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to write GM Edge routes", e);
        }
    }

    private List<String> split(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return List.copyOf(items);
    }

    private static String join(List<String> items) {
        return String.join(",", items == null ? List.of() : items);
    }

    private List<String> normalizeList(List<String> values, String label) {
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            String trimmed = requireText(value, label + " cannot be blank");
            require(!trimmed.contains(",") && !trimmed.contains("\r") && !trimmed.contains("\n"),
                    label + " contains invalid characters");
            normalized.add(trimmed);
        }
        return List.copyOf(normalized);
    }

    private List<String> changedFields(GmEdgePolicySettingsUpdate update) {
        if (update == null) {
            return List.of("GENERAL");
        }
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        if (update.enabled() != null) {
            fields.add("enabled");
        }
        if (update.inbound() != null) {
            fields.add("inbound");
        }
        if (update.outbound() != null) {
            fields.add("outbound");
        }
        if (update.postfix() != null) {
            fields.add("postfix");
        }
        if (update.tls() != null) {
            fields.add("tls");
        }
        if (update.limits() != null) {
            fields.add("limits");
        }
        if (update.routes() != null) {
            fields.add("routes");
        }
        return fields.isEmpty() ? List.of("GENERAL") : List.copyOf(fields);
    }

    private int requirePort(int port, String label) {
        require(port > 0 && port <= 65535, label + " must be between 1 and 65535");
        return port;
    }

    private int requirePositive(int value, String label) {
        require(value > 0, label + " must be positive");
        return value;
    }

    private String requireText(String value, String message) {
        String trimmed = value != null ? value.trim() : "";
        require(!trimmed.isBlank(), message);
        return trimmed;
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record RouteRecord(
            String domainPattern,
            String targetHost,
            int targetPort,
            String security
    ) {
    }
}
