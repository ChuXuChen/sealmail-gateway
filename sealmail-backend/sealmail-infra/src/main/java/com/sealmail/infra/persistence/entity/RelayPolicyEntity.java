package com.sealmail.infra.persistence.entity;

import com.sealmail.domain.mailsecurity.SmtpTransportSecurity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "relay_policy")
public class RelayPolicyEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "host", nullable = false, length = 254)
    private String host;

    @Column(name = "port", nullable = false)
    private int port;

    @Column(name = "use_tls", nullable = false)
    private boolean useTls;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_security", nullable = false, length = 16)
    private SmtpTransportSecurity transportSecurity = SmtpTransportSecurity.NONE;

    @Column(name = "username", length = 254)
    private String username;

    @Column(name = "password_secret_ref", length = 1024)
    private String passwordSecretRef;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    @Column(name = "envelope_from", length = 254)
    private String envelopeFrom;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private long version;
}
