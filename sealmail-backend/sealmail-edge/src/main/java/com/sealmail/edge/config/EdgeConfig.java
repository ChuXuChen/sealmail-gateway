package com.sealmail.edge.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

public record EdgeConfig(
        Inbound inbound,
        Outbound outbound,
        Postfix postfix,
        Tls tls,
        Admin admin,
        List<Route> routes,
        Duration connectTimeout,
        Duration readTimeout,
        int maxMessageSizeBytes,
        int maxLineLengthBytes,
        int maxRecipients
) {

    public static final int DEFAULT_GM_STARTTLS_PORT = 2525;
    public static final int DEFAULT_GM_IMPLICIT_TLS_PORT = 2465;
    public static final int DEFAULT_GM_OUTBOUND_PORT = 2526;

    public EdgeConfig {
        Objects.requireNonNull(inbound, "inbound must not be null");
        Objects.requireNonNull(outbound, "outbound must not be null");
        Objects.requireNonNull(postfix, "postfix must not be null");
        Objects.requireNonNull(tls, "tls must not be null");
        Objects.requireNonNull(admin, "admin must not be null");
        routes = List.copyOf(routes == null ? List.of() : routes);
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(10) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(60) : readTimeout;
        if (maxMessageSizeBytes <= 0) {
            throw new IllegalArgumentException("maxMessageSizeBytes must be positive");
        }
        if (maxLineLengthBytes <= 0) {
            throw new IllegalArgumentException("maxLineLengthBytes must be positive");
        }
        if (maxRecipients <= 0) {
            throw new IllegalArgumentException("maxRecipients must be positive");
        }
        validate(inbound, outbound, postfix, tls, admin);
    }

    public static EdgeConfig load(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return from(properties);
    }

    public static EdgeConfig from(Properties properties) {
        Inbound inbound = new Inbound(
                bool(properties, "edge.inbound.enabled", true),
                string(properties, "edge.inbound.bind-address", "0.0.0.0"),
                integer(properties, "edge.inbound.starttls-port", DEFAULT_GM_STARTTLS_PORT),
                integer(properties, "edge.inbound.implicit-tls-port", DEFAULT_GM_IMPLICIT_TLS_PORT),
                integer(properties, "edge.inbound.backlog", 128),
                integer(properties, "edge.inbound.max-connections", 1024)
        );
        Outbound outbound = new Outbound(
                bool(properties, "edge.outbound.enabled", true),
                string(properties, "edge.outbound.bind-address", "127.0.0.1"),
                integer(properties, "edge.outbound.port", DEFAULT_GM_OUTBOUND_PORT),
                integer(properties, "edge.outbound.backlog", 128),
                integer(properties, "edge.outbound.max-connections", 512)
        );
        Postfix postfix = new Postfix(
                string(properties, "edge.postfix.host", "127.0.0.1"),
                integer(properties, "edge.postfix.port", 2530)
        );
        Tls tls = new Tls(
                split(properties, "edge.tls.protocols", List.of("TLCPv1.1", "TLCP", "TLSv1.3")),
                split(properties, "edge.tls.cipher-suites", List.of("TLS_SM4_GCM_SM3", "TLS_SM4_CCM_SM3")),
                pathOrNull(properties, "edge.tls.key-store"),
                string(properties, "edge.tls.key-store-password", ""),
                string(properties, "edge.tls.key-store-type", "PKCS12"),
                pathOrNull(properties, "edge.tls.trust-store"),
                string(properties, "edge.tls.trust-store-password", ""),
                string(properties, "edge.tls.trust-store-type", "PKCS12"),
                bool(properties, "edge.tls.trust-all", false)
        );
        Admin admin = new Admin(
                bool(properties, "edge.admin.enabled", true),
                string(properties, "edge.admin.bind-address", "127.0.0.1"),
                integer(properties, "edge.admin.port", 2727)
        );
        return new EdgeConfig(
                inbound,
                outbound,
                postfix,
                tls,
                admin,
                routes(properties),
                Duration.ofMillis(integer(properties, "edge.connect-timeout-ms", 10_000)),
                Duration.ofMillis(integer(properties, "edge.read-timeout-ms", 60_000)),
                integer(properties, "edge.max-message-size-bytes", 52_428_800),
                integer(properties, "edge.max-line-length-bytes", 16_384),
                integer(properties, "edge.max-recipients", 100)
        );
    }

    public Properties toProperties() {
        Properties properties = new Properties();
        properties.setProperty("edge.inbound.enabled", Boolean.toString(inbound.enabled()));
        properties.setProperty("edge.inbound.bind-address", inbound.bindAddress());
        properties.setProperty("edge.inbound.starttls-port", Integer.toString(inbound.startTlsPort()));
        properties.setProperty("edge.inbound.implicit-tls-port", Integer.toString(inbound.implicitTlsPort()));
        properties.setProperty("edge.inbound.backlog", Integer.toString(inbound.backlog()));
        properties.setProperty("edge.inbound.max-connections", Integer.toString(inbound.maxConnections()));
        properties.setProperty("edge.outbound.enabled", Boolean.toString(outbound.enabled()));
        properties.setProperty("edge.outbound.bind-address", outbound.bindAddress());
        properties.setProperty("edge.outbound.port", Integer.toString(outbound.port()));
        properties.setProperty("edge.outbound.backlog", Integer.toString(outbound.backlog()));
        properties.setProperty("edge.outbound.max-connections", Integer.toString(outbound.maxConnections()));
        properties.setProperty("edge.postfix.host", postfix.host());
        properties.setProperty("edge.postfix.port", Integer.toString(postfix.port()));
        properties.setProperty("edge.tls.protocols", String.join(",", tls.protocols()));
        properties.setProperty("edge.tls.cipher-suites", String.join(",", tls.cipherSuites()));
        properties.setProperty("edge.tls.key-store", tls.keyStore() == null ? "" : tls.keyStore().toString());
        properties.setProperty("edge.tls.key-store-password", tls.keyStorePassword());
        properties.setProperty("edge.tls.key-store-type", tls.keyStoreType());
        properties.setProperty("edge.tls.trust-store", tls.trustStore() == null ? "" : tls.trustStore().toString());
        properties.setProperty("edge.tls.trust-store-password", tls.trustStorePassword());
        properties.setProperty("edge.tls.trust-store-type", tls.trustStoreType());
        properties.setProperty("edge.tls.trust-all", Boolean.toString(tls.trustAll()));
        properties.setProperty("edge.admin.enabled", Boolean.toString(admin.enabled()));
        properties.setProperty("edge.admin.bind-address", admin.bindAddress());
        properties.setProperty("edge.admin.port", Integer.toString(admin.port()));
        properties.setProperty("edge.connect-timeout-ms", Long.toString(connectTimeout.toMillis()));
        properties.setProperty("edge.read-timeout-ms", Long.toString(readTimeout.toMillis()));
        properties.setProperty("edge.max-message-size-bytes", Integer.toString(maxMessageSizeBytes));
        properties.setProperty("edge.max-line-length-bytes", Integer.toString(maxLineLengthBytes));
        properties.setProperty("edge.max-recipients", Integer.toString(maxRecipients));
        properties.setProperty("edge.outbound.routes", routeString());
        return properties;
    }

    private String routeString() {
        return String.join(",", routes.stream()
                .map(route -> route.domainPattern() + "=" + route.security() + "://"
                        + route.host() + ":" + route.port())
                .toList());
    }

    private static void validate(Inbound inbound, Outbound outbound, Postfix postfix, Tls tls, Admin admin) {
        requirePort(inbound.startTlsPort(), "edge.inbound.starttls-port");
        requirePort(inbound.implicitTlsPort(), "edge.inbound.implicit-tls-port");
        requirePort(outbound.port(), "edge.outbound.port");
        requirePort(postfix.port(), "edge.postfix.port");
        requirePort(admin.port(), "edge.admin.port");
        requirePositive(inbound.backlog(), "edge.inbound.backlog");
        requirePositive(outbound.backlog(), "edge.outbound.backlog");
        requirePositive(inbound.maxConnections(), "edge.inbound.max-connections");
        requirePositive(outbound.maxConnections(), "edge.outbound.max-connections");
        requireText(inbound.bindAddress(), "edge.inbound.bind-address");
        requireText(outbound.bindAddress(), "edge.outbound.bind-address");
        requireText(postfix.host(), "edge.postfix.host");
        requireText(admin.bindAddress(), "edge.admin.bind-address");
        requireNoListenerConflicts(inbound, outbound, admin);
        validateTls(tls);
    }

    private static void requireNoListenerConflicts(Inbound inbound, Outbound outbound, Admin admin) {
        Set<String> listeners = new HashSet<>();
        if (inbound.enabled()) {
            addListener(listeners, inbound.bindAddress(), inbound.startTlsPort(), "edge.inbound.starttls-port");
            addListener(listeners, inbound.bindAddress(), inbound.implicitTlsPort(), "edge.inbound.implicit-tls-port");
        }
        if (outbound.enabled()) {
            addListener(listeners, outbound.bindAddress(), outbound.port(), "edge.outbound.port");
        }
        if (admin.enabled()) {
            addListener(listeners, admin.bindAddress(), admin.port(), "edge.admin.port");
        }
    }

    private static void addListener(Set<String> listeners, String bindAddress, int port, String label) {
        String normalized = normalizeAddress(bindAddress);
        String exact = normalized + ":" + port;
        String wildcard = "*:" + port;
        if (listeners.contains(exact) || listeners.contains(wildcard)
                || (isWildcardAddress(normalized) && listeners.stream().anyMatch(item -> item.endsWith(":" + port)))) {
            throw new IllegalArgumentException("Listener port conflict at " + label + "=" + port);
        }
        listeners.add(isWildcardAddress(normalized) ? wildcard : exact);
    }

    private static void validateTls(Tls tls) {
        if (tls.protocols().isEmpty()) {
            throw new IllegalArgumentException("edge.tls.protocols must not be empty");
        }
        for (String protocol : tls.protocols()) {
            String normalized = protocol.toUpperCase(Locale.ROOT);
            if (!normalized.equals("TLCPV1.1") && !normalized.equals("TLCP") && !normalized.equals("TLSV1.3")) {
                throw new IllegalArgumentException("edge.tls.protocols only supports TLCP and ShangMi TLS 1.3");
            }
        }
        requireText(tls.keyStoreType(), "edge.tls.key-store-type");
        requireText(tls.trustStoreType(), "edge.tls.trust-store-type");
    }

    private static List<Route> routes(Properties properties) {
        String value = properties.getProperty("edge.outbound.routes", "").trim();
        if (value.isEmpty()) {
            return List.of();
        }
        List<Route> routes = new ArrayList<>();
        for (String entry : value.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] pair = trimmed.split("=", 2);
            if (pair.length != 2 || pair[0].isBlank() || pair[1].isBlank()) {
                throw new IllegalArgumentException("Invalid route entry: " + trimmed);
            }
            Target target = parseTarget(pair[1].trim());
            routes.add(new Route(pair[0].trim().toLowerCase(Locale.ROOT), target.host(), target.port(), target.security()));
        }
        return routes;
    }

    private static Target parseTarget(String value) {
        String target = value;
        RouteSecurity security = RouteSecurity.STARTTLS;
        int scheme = value.indexOf("://");
        if (scheme >= 0) {
            String schemeName = value.substring(0, scheme).trim().toUpperCase(Locale.ROOT).replace('-', '_');
            try {
                security = RouteSecurity.valueOf(schemeName);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid route security: " + schemeName, e);
            }
            target = value.substring(scheme + 3);
        }
        String[] hostPort = target.split(":", 2);
        if (hostPort.length != 2 || hostPort[0].isBlank()) {
            throw new IllegalArgumentException("Invalid route target: " + value);
        }
        return new Target(hostPort[0].trim(), parseInt(hostPort[1].trim()), security);
    }

    private static boolean bool(Properties properties, String key, boolean defaultValue) {
        return Boolean.parseBoolean(properties.getProperty(key, Boolean.toString(defaultValue)).trim());
    }

    private static int integer(Properties properties, String key, int defaultValue) {
        return parseInt(properties.getProperty(key, Integer.toString(defaultValue)).trim());
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer: " + value, e);
        }
    }

    private static void requirePort(int port, String label) {
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException(label + " must be between 1 and 65535");
        }
    }

    private static void requirePositive(int value, String label) {
        if (value <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
    }

    private static String normalizeAddress(String address) {
        return address.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isWildcardAddress(String address) {
        return "*".equals(address) || "0.0.0.0".equals(address) || "::".equals(address) || "[::]".equals(address);
    }

    private static String string(Properties properties, String key, String defaultValue) {
        return resolveEnv(properties.getProperty(key, defaultValue).trim());
    }

    private static String resolveEnv(String value) {
        if (value.startsWith("${") && value.endsWith("}") && value.length() > 3) {
            String envName = value.substring(2, value.length() - 1);
            return System.getenv().getOrDefault(envName, "");
        }
        return value;
    }

    private static List<String> split(Properties properties, String key, List<String> defaultValue) {
        String value = properties.getProperty(key, String.join(",", defaultValue)).trim();
        if (value.isEmpty()) {
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

    private static Path pathOrNull(Properties properties, String key) {
        String value = properties.getProperty(key, "").trim();
        return value.isEmpty() ? null : Path.of(value);
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

    public record Outbound(boolean enabled, String bindAddress, int port, int backlog, int maxConnections) {
    }

    public record Postfix(String host, int port) {
    }

    public record Tls(
            List<String> protocols,
            List<String> cipherSuites,
            Path keyStore,
            String keyStorePassword,
            String keyStoreType,
            Path trustStore,
            String trustStorePassword,
            String trustStoreType,
            boolean trustAll
    ) {
        public Tls {
            protocols = List.copyOf(protocols == null ? List.of() : protocols);
            cipherSuites = List.copyOf(cipherSuites == null ? List.of() : cipherSuites);
        }
    }

    public record Admin(boolean enabled, String bindAddress, int port) {
    }

    public enum RouteSecurity {
        STARTTLS,
        IMPLICIT_TLS
    }

    public record Route(String domainPattern, String host, int port, RouteSecurity security) {
        public Route {
            if (domainPattern == null || domainPattern.isBlank()) {
                throw new IllegalArgumentException("domainPattern must not be blank");
            }
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("host must not be blank");
            }
            if (port <= 0 || port > 65_535) {
                throw new IllegalArgumentException("port must be between 1 and 65535");
            }
            if (security == null) {
                throw new IllegalArgumentException("security must not be null");
            }
        }
    }

    private record Target(String host, int port, RouteSecurity security) {
    }
}
