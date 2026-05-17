package com.sealmail.edge.admin;

import com.sealmail.edge.config.EdgeConfig;
import com.sealmail.edge.metrics.EdgeMetrics;
import com.sealmail.edge.smtp.SmtpEdgeServer;
import com.sealmail.edge.tls.EdgeTlsContextFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EdgeAdminServer implements AutoCloseable {
    private final EdgeConfig.Admin admin;
    private final EdgeConfig config;
    private final EdgeTlsContextFactory tlsContextFactory;
    private final List<SmtpEdgeServer> smtpServers;
    private final EdgeMetrics metrics;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private HttpServer server;

    public EdgeAdminServer(EdgeConfig.Admin admin,
                           EdgeConfig config,
                           EdgeTlsContextFactory tlsContextFactory,
                           List<SmtpEdgeServer> smtpServers,
                           EdgeMetrics metrics) {
        this.admin = admin;
        this.config = config;
        this.tlsContextFactory = tlsContextFactory;
        this.smtpServers = smtpServers;
        this.metrics = metrics;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(admin.bindAddress(), admin.port()), 16);
        server.setExecutor(executor);
        server.createContext("/health", exchange -> writeJson(exchange, healthJson()));
        server.createContext("/status", exchange -> writeJson(exchange, statusJson()));
        server.createContext("/metrics", exchange -> writeJson(exchange, metricsJson()));
        server.start();
    }

    private String healthJson() {
        boolean ready = smtpServers.stream().allMatch(SmtpEdgeServer::running);
        return "{\"ready\":" + ready + ",\"listeners\":" + smtpServers.size() + "}";
    }

    private String statusJson() {
        EdgeTlsContextFactory.TlsDiagnostics tls = tlsContextFactory.diagnostics();
        return "{"
                + "\"ready\":" + smtpServers.stream().allMatch(SmtpEdgeServer::running) + ","
                + "\"listeners\":" + listenersJson() + ","
                + "\"tls\":{"
                + "\"configuredProtocols\":" + jsonArray(config.tls().protocols()) + ","
                + "\"enabledProtocols\":" + jsonArray(tls.enabledProtocols()) + ","
                + "\"configuredCipherSuites\":" + jsonArray(config.tls().cipherSuites()) + ","
                + "\"enabledCipherSuites\":" + jsonArray(tls.enabledCipherSuites()) + ","
                + "\"keyStoreConfigured\":" + (config.tls().keyStore() != null) + ","
                + "\"trustStoreConfigured\":" + (config.tls().trustStore() != null) + ","
                + "\"trustAll\":" + config.tls().trustAll()
                + "},"
                + "\"routes\":" + routesJson()
                + "}";
    }

    private String metricsJson() {
        EdgeMetrics.Snapshot snapshot = metrics.snapshot();
        return "{"
                + "\"startedAt\":\"" + escape(snapshot.startedAt().toString()) + "\","
                + "\"acceptedConnections\":" + snapshot.acceptedConnections() + ","
                + "\"rejectedConnections\":" + snapshot.rejectedConnections() + ","
                + "\"completedSessions\":" + snapshot.completedSessions() + ","
                + "\"relayedMessages\":" + snapshot.relayedMessages() + ","
                + "\"failedMessages\":" + snapshot.failedMessages() + ","
                + "\"temporaryFailures\":" + snapshot.temporaryFailures() + ","
                + "\"permanentFailures\":" + snapshot.permanentFailures() + ","
                + "\"oversizedMessages\":" + snapshot.oversizedMessages() + ","
                + "\"relayedBytes\":" + snapshot.relayedBytes()
                + "}";
    }

    private String listenersJson() {
        return "[" + String.join(",", smtpServers.stream()
                .map(SmtpEdgeServer::status)
                .map(status -> "{"
                        + "\"name\":\"" + escape(status.name()) + "\","
                        + "\"bindAddress\":\"" + escape(status.bindAddress()) + "\","
                        + "\"port\":" + status.port() + ","
                        + "\"mode\":\"" + escape(status.mode()) + "\","
                        + "\"running\":" + status.running() + ","
                        + "\"activeConnections\":" + status.activeConnections() + ","
                        + "\"maxConnections\":" + status.maxConnections()
                        + "}")
                .toList()) + "]";
    }

    private String routesJson() {
        return "[" + String.join(",", config.routes().stream()
                .map(route -> "{"
                        + "\"domainPattern\":\"" + escape(route.domainPattern()) + "\","
                        + "\"targetHost\":\"" + escape(route.host()) + "\","
                        + "\"targetPort\":" + route.port() + ","
                        + "\"security\":\"" + escape(route.security().name()) + "\""
                        + "}")
                .toList()) + "]";
    }

    private static void writeJson(HttpExchange exchange, String body) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String jsonArray(List<String> values) {
        return "[" + String.join(",", values.stream()
                .map(value -> "\"" + escape(value) + "\"")
                .toList()) + "]";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(ch);
            }
        }
        return builder.toString();
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
        }
        executor.shutdownNow();
    }
}
