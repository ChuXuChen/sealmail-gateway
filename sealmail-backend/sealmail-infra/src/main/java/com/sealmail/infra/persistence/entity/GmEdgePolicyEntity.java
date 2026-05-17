package com.sealmail.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "gm_edge_policy")
public class GmEdgePolicyEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "inbound_enabled", nullable = false)
    private boolean inboundEnabled;

    @Column(name = "inbound_bind_address", nullable = false, length = 128)
    private String inboundBindAddress;

    @Column(name = "inbound_starttls_port", nullable = false)
    private int inboundStartTlsPort;

    @Column(name = "inbound_implicit_tls_port", nullable = false)
    private int inboundImplicitTlsPort;

    @Column(name = "inbound_backlog", nullable = false)
    private int inboundBacklog;

    @Column(name = "inbound_max_connections", nullable = false)
    private int inboundMaxConnections;

    @Column(name = "outbound_enabled", nullable = false)
    private boolean outboundEnabled;

    @Column(name = "outbound_bind_address", nullable = false, length = 128)
    private String outboundBindAddress;

    @Column(name = "outbound_smart_host_port", nullable = false)
    private int outboundSmartHostPort;

    @Column(name = "outbound_backlog", nullable = false)
    private int outboundBacklog;

    @Column(name = "outbound_max_connections", nullable = false)
    private int outboundMaxConnections;

    @Column(name = "postfix_host", nullable = false, length = 254)
    private String postfixHost;

    @Column(name = "postfix_port", nullable = false)
    private int postfixPort;

    @Column(name = "tls_protocols", nullable = false, length = 512)
    private String tlsProtocols;

    @Column(name = "tls_cipher_suites", nullable = false, length = 2048)
    private String tlsCipherSuites;

    @Column(name = "tls_key_store_path", length = 1024)
    private String tlsKeyStorePath;

    @Column(name = "tls_key_store_password_secret_ref", length = 1024)
    private String tlsKeyStorePasswordSecretRef;

    @Column(name = "tls_key_store_type", nullable = false, length = 32)
    private String tlsKeyStoreType;

    @Column(name = "tls_trust_store_path", length = 1024)
    private String tlsTrustStorePath;

    @Column(name = "tls_trust_store_password_secret_ref", length = 1024)
    private String tlsTrustStorePasswordSecretRef;

    @Column(name = "tls_trust_store_type", nullable = false, length = 32)
    private String tlsTrustStoreType;

    @Column(name = "tls_trust_all", nullable = false)
    private boolean tlsTrustAll;

    @Column(name = "connect_timeout_ms", nullable = false)
    private int connectTimeoutMs;

    @Column(name = "read_timeout_ms", nullable = false)
    private int readTimeoutMs;

    @Column(name = "max_message_size_bytes", nullable = false)
    private int maxMessageSizeBytes;

    @Column(name = "max_line_length_bytes", nullable = false)
    private int maxLineLengthBytes;

    @Column(name = "max_recipients", nullable = false)
    private int maxRecipients;

    @Column(name = "routes_json", nullable = false, columnDefinition = "TEXT")
    private String routesJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private long version;
}
